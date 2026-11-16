package come.ying.lifenote.service;

import come.ying.lifenote.common.enums.ErrorCode;
import come.ying.lifenote.dto.request.DeleteAccountRequest;
import come.ying.lifenote.entity.User;
import come.ying.lifenote.exception.BusinessException;
import come.ying.lifenote.repository.UserRepository;
import come.ying.lifenote.repository.UserSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSettingsRepository userSettingsRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private S3StorageService s3StorageService;
    public User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
    @Transactional
    public User updateUsername(UUID userId,String username) {
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.setUsername(username);
        userRepository.save(user);
        return user;
    }

    @Transactional
    public void updatePassword(UUID userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String encryptedPassword = passwordEncoder.encode(password);
        user.setPasswordHash(encryptedPassword);
        userRepository.save(user);
    }
    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));
        //check if the password is right
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        // Uploaded originals first: once the DB rows are gone the object keys are
        // unrecoverable, so a failed wipe is logged with the prefix for manual cleanup
        // and account deletion still goes through.
        try {
            s3StorageService.deleteAllForUser(userId);
        } catch (Exception e) {
            log.error("S3 wipe failed during account deletion, orphan prefix users/{}: {}",
                    userId, e.getMessage());
        }
        // delete settings explicitly instead of relying on an FK cascade that may not exist yet
        userSettingsRepository.deleteById(userId);
        userRepository.delete(user);
    }
}
