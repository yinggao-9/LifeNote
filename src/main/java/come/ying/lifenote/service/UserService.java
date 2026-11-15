package come.ying.lifenote.service;

import come.ying.lifenote.common.enums.ErrorCode;
import come.ying.lifenote.dto.request.DeleteAccountRequest;
import come.ying.lifenote.entity.User;
import come.ying.lifenote.exception.BusinessException;
import come.ying.lifenote.repository.UserRepository;
import come.ying.lifenote.repository.UserSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSettingsRepository userSettingsRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
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
        // delete settings explicitly instead of relying on an FK cascade that may not exist yet
        userSettingsRepository.deleteById(userId);
        userRepository.delete(user);
    }
}
