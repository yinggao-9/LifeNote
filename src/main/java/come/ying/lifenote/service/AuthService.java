package come.ying.lifenote.service;

import come.ying.lifenote.common.enums.ErrorCode;
import come.ying.lifenote.common.properties.JwtProperties;
import come.ying.lifenote.common.utils.JwtUtils;
import come.ying.lifenote.dto.request.LoginRequest;
import come.ying.lifenote.dto.request.RegisterRequest;
import come.ying.lifenote.dto.response.LoginResponse;
import come.ying.lifenote.entity.User;
import come.ying.lifenote.entity.UserSetting;
import come.ying.lifenote.exception.BusinessException;
import come.ying.lifenote.repository.UserRepository;
import come.ying.lifenote.repository.UserSettingsRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSettingsRepository userSettingsRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtProperties jwtProperties;
    //register
    @Transactional
    public void register(RegisterRequest request) {
        request.setEmail(normalizeEmail(request.getEmail()));
        request.setUsername(request.getUsername() == null ? null : request.getUsername().trim());
        User user = new User();
        BeanUtils.copyProperties(request,user);
        //1.Verify whether the email is existed
        if (userRepository.existsByEmail(user.getEmail())){
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        //2.Convert the password to a hashed password
        String encryptedPassword = passwordEncoder.encode(request.getPassword());
        user.setPasswordHash(encryptedPassword);
        //3.Store data in the database
        User saved = userRepository.save(user);
        //4.Insert this user's default settings
        UserSetting userSetting = new UserSetting();
        userSetting.setId(saved.getId());
        userSettingsRepository.save(userSetting);
    }

    //login
    public LoginResponse login(LoginRequest request) {
        //1.check whether the user is existed
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(()-> new BusinessException(ErrorCode.PASSWORD_ERROR));

        //2.check if the password is right
        boolean passwordMatch = passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        );
        if (!passwordMatch) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        //3.create jwt
        Map<String,Object> claims = new HashMap<>();
        //4.store user id
        claims.put("userId",user.getId());
        String token = JwtUtils.createJwt(jwtProperties.getSecretKey(),jwtProperties.getTtl(),claims);
        LoginResponse loginResponse =   LoginResponse.builder()
                                        .userId(user.getId())
                                        .email(user.getEmail())
                                        .username(user.getUsername())
                                        .token(token)
                                        .build();
        return loginResponse;
    }

    //normalize email: trim + lowercase, so register and login stay consistent
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
