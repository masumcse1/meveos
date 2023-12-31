package org.meveo.service.notification;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.meveo.admin.exception.BusinessException;
import org.meveo.commons.utils.StringUtils;
import org.meveo.elresolver.ELException;
import org.meveo.model.notification.NotificationHistoryStatusEnum;
import org.meveo.model.notification.WebHook;
import org.meveo.model.notification.WebHookMethodEnum;
import org.meveo.security.MeveoUser;
import org.meveo.security.keycloak.CurrentUserProvider;
import org.meveo.service.base.MeveoValueExpressionWrapper;
import org.meveo.service.script.ScriptInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Edward P. Legaspi
 * @lastModifiedVersion 5.0
 */
@Stateless
public class WebHookNotifier {

	private static Logger log = LoggerFactory.getLogger(WebHookNotifier.class);

    @Inject
    NotificationHistoryService notificationHistoryService;

    @Inject
    ScriptInstanceService scriptInstanceService;

    @Inject
    private CurrentUserProvider currentUserProvider;

    private String evaluate(String expression, Object entityOrEvent, Map<String, Object> context) throws ELException {
        HashMap<Object, Object> userMap = new HashMap<Object, Object>();
        userMap.put("event", entityOrEvent);
        userMap.put("context", context);
        return (String) MeveoValueExpressionWrapper.evaluateExpression(expression, userMap, String.class);
    }

    private Map<String, String> evaluateMap(Map<String, String> map, Object entityOrEvent, Map<String, Object> context) throws BusinessException, ELException {
        Map<String, String> result = new HashMap<String, String>();
        HashMap<Object, Object> userMap = new HashMap<Object, Object>();
        userMap.put("event", entityOrEvent);
        userMap.put("context", context);

        for (String key : map.keySet()) {
            result.put(key, (String) MeveoValueExpressionWrapper.evaluateExpression(map.get(key), userMap, String.class));
        }

        return result;
    }

    /**
     * Access web URL as fired notification result
     * 
     * @param webHook Webhook type notification that was fired
     * @param entityOrEvent Entity or event that triggered notification
     * @param context Execution context
     * @param lastCurrentUser Current user. In case of multitenancy, when user authentication is forced as result of a fired trigger (scheduled jobs, other timed event
     *        expirations), current user might be lost, thus there is a need to reestablish.
     */
    @Asynchronous
    public void sendRequest(WebHook webHook, Object entityOrEvent, Map<String, Object> context, MeveoUser lastCurrentUser) {

        currentUserProvider.reestablishAuthentication(lastCurrentUser);

        log.debug("webhook sendRequest");
        String result = "";

        try {
            String url = webHook.getHttpProtocol().name().toLowerCase() + "://" + webHook.getHost().replace("http://", "");
            if (webHook.getPort() != null) {
                url += ":" + webHook.getPort();
            }

            if (!StringUtils.isBlank(webHook.getPage())) {
                String page = evaluate(webHook.getPage(), entityOrEvent, context);
                url += ((url.endsWith("/") || page.startsWith("/")) ? "" : "/") + page;
            }
            Map<String, String> params = evaluateMap(webHook.getWebhookParams(), entityOrEvent, context);

            String paramQuery = "";
            String sep = "";
            for (String paramKey : params.keySet()) {
                paramQuery += sep + URLEncoder.encode(paramKey, "UTF-8") + "=" + URLEncoder.encode(params.get(paramKey), "UTF-8");
                sep = "&";
            }
            String bodyEL_evaluated = null;
            log.debug("paramQuery={}", paramQuery);
            if (WebHookMethodEnum.HTTP_GET == webHook.getHttpMethod()) {
                url += "?" + paramQuery;
            } else if (WebHookMethodEnum.HTTP_POST == webHook.getHttpMethod()) {
                bodyEL_evaluated = evaluate(webHook.getBodyEL(), entityOrEvent, context);
                log.debug("Evaluated BodyEL={}", bodyEL_evaluated);
                if (!StringUtils.isBlank(bodyEL_evaluated)) {
                    paramQuery += "&" + bodyEL_evaluated;
                }
            }
            log.debug("webhook url: {}", url);
            URL obj = new URL(url);

            HttpURLConnection conn = (HttpURLConnection) obj.openConnection();

            Map<String, String> headers = evaluateMap(webHook.getHeaders(), entityOrEvent, context);
            if (!StringUtils.isBlank(webHook.getUsername()) && !headers.containsKey("Authorization")) {
                byte[] bytes = Base64.encodeBase64((webHook.getUsername() + ":" + webHook.getPassword()).getBytes());
                headers.put("Authorization", "Basic " + new String(bytes));
            }

            for (String key : headers.keySet()) {
                conn.setRequestProperty(key, headers.get(key));
            }

            if (WebHookMethodEnum.HTTP_GET == webHook.getHttpMethod()) {
                conn.setRequestMethod("GET");
            } else if (WebHookMethodEnum.HTTP_POST == webHook.getHttpMethod()) {
                conn.setRequestMethod("POST");
            } else if (WebHookMethodEnum.HTTP_PUT == webHook.getHttpMethod()) {
                conn.setRequestMethod("PUT");
            } else if (WebHookMethodEnum.HTTP_DELETE == webHook.getHttpMethod()) {
                conn.setRequestMethod("DELETE");
            }
            conn.setUseCaches(false);

            if (WebHookMethodEnum.HTTP_GET != webHook.getHttpMethod() && WebHookMethodEnum.HTTP_DELETE != webHook.getHttpMethod()) {
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write(paramQuery);
                writer.flush();
                writer.close();
                os.close();
            }
            int responseCode = conn.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            result = response.toString();
            if (responseCode != 200) {
                try {
                    log.debug("webhook httpStatus error : " + responseCode + " response=" + result);
                    notificationHistoryService.create(webHook, entityOrEvent, "http error status=" + responseCode + " response=" + result, NotificationHistoryStatusEnum.FAILED);
                } catch (BusinessException e2) {
                    log.error("Failed to create webhook ", entityOrEvent);
                }
            } else {
                if (webHook.getFunction() != null) {
                    HashMap<Object, Object> userMap = new HashMap<Object, Object>();
                    userMap.put("event", entityOrEvent);
                    userMap.put("response", result);

                    try {
                        Map<String, Object> paramsEvaluated = new HashMap<String, Object>();

                        for (@SuppressWarnings("rawtypes")
                        Map.Entry entry : webHook.getParams().entrySet()) {
                            paramsEvaluated.put((String) entry.getKey(), MeveoValueExpressionWrapper.evaluateExpression((String) entry.getValue(), userMap, String.class));
                        }
                        paramsEvaluated.put("response", result);
                        scriptInstanceService.execute(webHook.getFunction().getCode(), paramsEvaluated);

                    } catch (Exception ee) {
                        log.error("Failed to execute a script {}", webHook.getFunction().getCode(), ee);
                    }
                }
                log.debug("webhook answer : " + result);
                notificationHistoryService.create(webHook, entityOrEvent, result, NotificationHistoryStatusEnum.SENT);

            }
        } catch (Exception e) {
            try {
                log.debug("webhook business error : ", e);
                notificationHistoryService.create(webHook, entityOrEvent, e.getMessage(),
                    e instanceof IOException ? NotificationHistoryStatusEnum.TO_RETRY : NotificationHistoryStatusEnum.FAILED);
            } catch (BusinessException e2) {
                log.error("Failed to create notification history", e2);

            }
        }
    }
}
