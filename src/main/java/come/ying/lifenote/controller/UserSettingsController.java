package come.ying.lifenote.controller;

import come.ying.lifenote.common.ApiResponse.ApiResponse;
import come.ying.lifenote.common.utils.BaseContext;
import come.ying.lifenote.dto.request.UserSettingsUpdateRequest;
import come.ying.lifenote.entity.UserSetting;
import come.ying.lifenote.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/api/users/me")
public class UserSettingsController {
    @Autowired
    private UserSettingsService userSettingsService;
    //View user settings
    @GetMapping("/settings")
    public ApiResponse getSettings(){
        UUID userId = BaseContext.getCurrentId();
        log.info("view logged-in user's settings: {}", userId);
        UserSetting settings = userSettingsService.getSettings(userId);
        return ApiResponse.success(settings);
    }
    //Update user settings
    @PutMapping("/settings")
    public ApiResponse updateSettings(@Valid @RequestBody UserSettingsUpdateRequest request){
        UUID userId = BaseContext.getCurrentId();
        log.info("Update logged-in user's settings: {}", userId);
        UserSetting setting = userSettingsService.updateSettings(userId,request);
        return ApiResponse.success(setting);
    }

}
