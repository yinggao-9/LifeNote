package come.ying.lifenote.controller;

import come.ying.lifenote.common.ApiResponse.ApiResponse;
import come.ying.lifenote.common.utils.BaseContext;
import come.ying.lifenote.dto.request.DeleteAccountRequest;
import come.ying.lifenote.entity.User;
import come.ying.lifenote.service.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@Validated
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService;
    // Get the logged-in user's info
    @PostMapping("/me")
    public ApiResponse getUser(){
        UUID userId = BaseContext.getCurrentId();
        log.info("get user info: {}", userId);
        User user = userService.getUser(userId);
        return ApiResponse.success(user);
    }
    //Update username
    @PatchMapping("/me")
    public ApiResponse updateUsername(@RequestParam @NotBlank @Size(min = 2, max = 30, message = "length must be between 2 and 30")
                                           String username){
        log.info("update username: {}", username);
        UUID userId = BaseContext.getCurrentId();
        User user = userService.updateUsername(userId,username);
        return ApiResponse.success(user);
    }
    //Update password
    @PutMapping("/me/password")
    public ApiResponse updatePassword(@RequestParam  @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
                                      String password){
        UUID userId = BaseContext.getCurrentId();
        log.info("update password: {}", userId);
        userService.updatePassword(userId,password);
        return ApiResponse.success();
    }
    //Delete account
    @DeleteMapping("/me")
    public ApiResponse deleteAccount(@RequestBody DeleteAccountRequest request){
        UUID userId = BaseContext.getCurrentId();
        log.info("Delete account: {}", userId);
        userService.deleteAccount(userId,request);
        return ApiResponse.success();
    }
}
