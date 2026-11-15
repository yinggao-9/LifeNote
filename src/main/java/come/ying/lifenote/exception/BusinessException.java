package come.ying.lifenote.exception;

import come.ying.lifenote.common.enums.ErrorCode;
import lombok.Data;

@Data
public class BusinessException extends RuntimeException{
    private Integer code; //status code
    //constructor with message
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }
    //constructor with code and message
    public BusinessException(Integer code,String message) {
        super(message);
        this.code = code;
    }
    //constructor with enums
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }
}
