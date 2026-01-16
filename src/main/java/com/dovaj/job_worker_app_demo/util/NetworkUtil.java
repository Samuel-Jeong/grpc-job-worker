package com.dovaj.job_worker_app_demo.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;

/**
 * packageName    : com.dovaj.job_worker_app_demo.util
 * fileName       : NetworkUtil
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : Network 유틸 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Slf4j
public class NetworkUtil {

    public static final String ANY_HOST = "0.0.0.0";
    public static final int DEFAULT_GRPC_PORT = 9091;

    private NetworkUtil() {
    }

    public static String getCurrentIp() {
        String ip = null;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    // IPv4이며 루프백이 아닌 실제 IP만 출력
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        ip = address.getHostAddress();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        return ip;
    }

    public static int pickAvailablePort(int preferred) {
        if (preferred <= 0) preferred = DEFAULT_GRPC_PORT;
        if (isPortFree(preferred)) return preferred;

        // 충돌 시, 인접 포트 탐색(최대 50개 범위 내)
        for (int p = preferred + 1; p < preferred + 50; p++) {
            if (isPortFree(p)) {
                log.warn("->SVC::Port {} in use. Falling back to {}", preferred, p);
                return p;
            }
        }
        // 그래도 없으면 OS가 할당하도록 0 사용
        log.warn("->SVC::No free port near {}. Using ephemeral port (0)", preferred);
        return 0;
    }

    public static boolean isPortFree(int port) {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(false);
            ss.bind(new InetSocketAddress("0.0.0.0", port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

}
