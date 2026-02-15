package demo.app.controllers;

import com.microsoft.applicationinsights.TelemetryClient;
import javax.servlet.RequestDispatcher;              // ✅ Boot 2.7 uses javax.*
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    @Autowired(required = false)
    private TelemetryClient telemetryClient;

    @RequestMapping("/error")
    public String handleError(Model model, HttpServletRequest request) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = (statusObj instanceof Integer) ? (Integer) statusObj : 0;

        // In Servlet spec the attribute key is "javax.servlet.error.exception"
        Object exObj = request.getAttribute("javax.servlet.error.exception");

        String message;
        if (exObj != null) {
            Exception ex;
            if (exObj instanceof Exception) {
                ex = (Exception) exObj;
            } else if (exObj instanceof Throwable) {
                ex = new Exception((Throwable) exObj);   // wrap Throwable for TelemetryClient
            } else {
                ex = new Exception(String.valueOf(exObj));
            }
            if (telemetryClient != null) {
                telemetryClient.trackException(ex);
            }
            message = ex.getMessage();
        } else {
            HttpStatus http = HttpStatus.resolve(status);
            message = (http != null) ? http.getReasonPhrase() : "Unexpected Error";
        }

        model.addAttribute("status", status);
        model.addAttribute("message", message);
        return "error"; // renders templates/error.ftl
    }

    // ❌ Do NOT implement getErrorPath(); removed since Spring Boot 2.6+
}

