package come.ying.lifenote.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LoginResponse {

    private UUID userId;
    private String email;
    private String username;
    private String token;
}
