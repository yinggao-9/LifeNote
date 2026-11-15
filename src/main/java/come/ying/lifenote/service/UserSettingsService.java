package come.ying.lifenote.service;

import come.ying.lifenote.common.enums.ErrorCode;
import come.ying.lifenote.dto.request.UserSettingsUpdateRequest;
import come.ying.lifenote.entity.UserSetting;
import come.ying.lifenote.exception.BusinessException;
import come.ying.lifenote.repository.UserSettingsRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

@Service
public class UserSettingsService {
    @Autowired
    private UserSettingsRepository userSettingsRepository;
    //AI model
    private static final Set<String> ALLOWED_MODELS = Set.of("gpt", "deepseek");
    public UserSetting getSettings(UUID userId) {
        return userSettingsRepository.findById(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_SETTINGS_NOT_FOUND));
    }

    public UserSetting updateSettings(UUID userId, UserSettingsUpdateRequest request) {
        UserSetting setting =userSettingsRepository.findById(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_SETTINGS_NOT_FOUND));
    // Time zone validation
        try {
            ZoneId.of(request.getTimeZone());
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.INVALID_TIME_ZONE);
        }
    // Model whitelist
        if (!ALLOWED_MODELS.contains(request.getAiModel())) {
            throw new BusinessException(ErrorCode.INVALID_AI_MODEL);
        }
        BeanUtils.copyProperties(request,setting);
        return userSettingsRepository.save(setting);
    }
}
