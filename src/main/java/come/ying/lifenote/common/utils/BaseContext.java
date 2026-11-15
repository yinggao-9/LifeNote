package come.ying.lifenote.common.utils;

import java.util.UUID;

public class BaseContext {
    private static ThreadLocal<UUID> threadLocal = new ThreadLocal<>();

    public static UUID getCurrentId() {
        return threadLocal.get();
    }

    public static void setCurrentId(UUID id){
        threadLocal.set(id);
    }
    public static void removeId(){
        threadLocal.remove();
    }
}
