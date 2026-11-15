package come.ying.lifenote.common.ApiResponse;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({"code", "msg", "data"})
public class ApiResponse<T> implements Serializable {
    private Integer code;//Status code (200: Success, 500: Error)
    private String msg;//Response message
    private T data;//Response payload

    //Success response without data
    public static<T> ApiResponse<T> success(){
        return new ApiResponse<>(200,"success",null);
    }
    //Success response with data
    public static<T> ApiResponse<T> success(T data){
        return new ApiResponse<>(200,"success",data);
    }
    //Failure response with default code 500
    public static<T> ApiResponse<T> fail(String msg){
        return new ApiResponse<>(500,msg,null);
    }
    //Failure response with custom code
    public static<T> ApiResponse<T> fail(Integer code,String msg){
        return new ApiResponse<>(code,msg,null);
    }
    //failure response with data
    public static<T> ApiResponse<T> fail(Integer code,String msg,T data){ return new ApiResponse<>(code,msg,data); }
}
