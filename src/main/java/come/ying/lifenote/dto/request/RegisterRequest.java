package come.ying.lifenote.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotBlank @Size(min = 2, max = 30)
    private String username;

    @NotBlank @Size(min = 8)
    private String password;
}
