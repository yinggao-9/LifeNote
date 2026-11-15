package come.ying.lifenote.interceptor;

import come.ying.lifenote.common.properties.JwtProperties;
import come.ying.lifenote.common.utils.BaseContext;
import come.ying.lifenote.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
@Slf4j
public class JwtTokenInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtProperties jwtProperties;
public boolean preHandle(HttpServletRequest request, HttpServletResponse response,Object handler) {
    // Skip non-controller handlers (e.g. static resources)
    if(!(handler instanceof HandlerMethod)){
        return true;
    }
    //1.Extract the token from the request header
    String token = request.getHeader((jwtProperties.getTokenName()));
    log.info("Token from header:{}",token != null? "found":"null");
    if(token == null || token.trim().isEmpty()){
        log.error("No token");
        response.setStatus(401);
        return false;
    }
    log.info("Parsing token...");
    //2.validate the token
    try {
        Claims claims = JwtUtils.parseJwt(jwtProperties.getSecretKey(),token);
        //store the user id in ThreadLocal
        String userIdStr = claims.get("userId", String.class);
        if (userIdStr == null) { response.setStatus(401); return false; }
        UUID userId = UUID.fromString(userIdStr);
        BaseContext.setCurrentId(userId);
        log.info("===Parsed userId from token: {} ====",userId);
        return true;
    } catch (Exception e) {
        log.error("===JWT validation failed: {} ====",e.getMessage());
        response.setStatus(401);
        return false;
    }
}

    // Tomcat reuses threads; leaving the id behind could leak one user's identity into the next request.
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.removeId();
    }
}
