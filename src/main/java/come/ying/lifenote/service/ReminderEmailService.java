package come.ying.lifenote.service;

import come.ying.lifenote.entity.Reminder;
import come.ying.lifenote.entity.User;
import come.ying.lifenote.entity.UserSetting;
import come.ying.lifenote.repository.UserRepository;
import come.ying.lifenote.repository.UserSettingsRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Best-effort email copy of a reminder. Every guard is a silent skip:
 * no mail configured, user turned email off, no sender address, send
 * failure - the in-app reminder already exists either way.
 */
@Slf4j
@Service
public class ReminderEmailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final String fromAddress;

    public ReminderEmailService(ObjectProvider<JavaMailSender> mailSender,
                                UserRepository userRepository,
                                UserSettingsRepository settingsRepository,
                                @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.fromAddress = fromAddress;
    }

    public void send(Reminder reminder) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            return; // spring.mail not configured yet - in-app only
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("spring.mail configured but spring.mail.username is empty; skipping email");
            return;
        }
        UserSetting setting = settingsRepository.findById(reminder.getUserId()).orElse(null);
        if (setting == null || !Boolean.TRUE.equals(setting.getEmailRemindersEnabled())) {
            return;
        }
        User user = userRepository.findById(reminder.getUserId()).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromAddress);
            helper.setTo(user.getEmail());
            helper.setSubject(reminder.getTitle());
            helper.setText(reminder.getBody(), false);
            sender.send(message);
            log.info("reminder email sent to {} ({})", user.getEmail(), reminder.getType());
        } catch (Exception e) {
            // never let a mail problem break reminder generation
            log.warn("reminder email to {} failed: {}", user.getEmail(), e.getMessage());
        }
    }
}
