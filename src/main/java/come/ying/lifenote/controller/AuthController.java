package come.ying.lifenote.controller;

import come.ying.lifenote.common.ApiResponse.ApiResponse;
import come.ying.lifenote.dto.request.LoginRequest;
import come.ying.lifenote.dto.request.RegisterRequest;
import come.ying.lifenote.dto.response.LoginResponse;
import come.ying.lifenote.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

//register and login
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;
    //register
    //never log the request object here: RegisterRequest.toString() contains the raw password
    @PostMapping("/register")
    public ApiResponse register(@Valid @RequestBody RegisterRequest request){
        log.info("register email={}", request.getEmail());
        authService.register(request);
        return ApiResponse.success();
    }
    //login
    @PostMapping("/login")
    public ApiResponse login(@Valid @RequestBody LoginRequest request){
        log.info("start login: {}", request.getEmail());
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response);
    }
    //login out
    @PostMapping("/logout")
    public ApiResponse logout() {
        // JWT is stateless, so the backend doesn't need to do anything here.
        // Just delete the token from the frontend.
        return ApiResponse.success("Logout successful");
    }

}
