package com.example.PadelCaleruela;

import com.example.PadelCaleruela.dto.WelockResponse;
import com.example.PadelCaleruela.service.WelockAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WelockClient {

    private final WebClient webClient;
    private final WelockAuthService auth;

    @Value("${welock.base-url}")
    private String baseUrl;

    @Value("${welock.app-id}")
    private String appId;

    public WelockClient(WebClient.Builder builder, WelockAuthService auth) {
        this.webClient = builder.build();
        this.auth = auth;
    }

    // ----------------------------------------------------------
    // REGISTRAR DISPOSITIVO (DeviceCreate)
    // ----------------------------------------------------------
    public WelockResponse registerDevice(String deviceNumber, String deviceName, String userId) {

        String token = auth.getToken();

        Map<String, Object> body = new HashMap<>();
        body.put("appID", appId);
        body.put("deviceNumber", deviceNumber);
        if (deviceName != null) body.put("deviceName", deviceName);
        if (userId != null) body.put("userID", userId);

        String url = baseUrl + "/API/Device/DeviceCreate";

        WelockResponse resp = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(WelockResponse.class)
                .block();

        if (resp == null || resp.getCode() != 0) {
            throw new RuntimeException("No se pudo registrar el dispositivo en Welock: " + resp);
        }

        return resp;
    }


    // ----------------------------------------------------------
    // COMANDO UNLOCK BLE (DeviceUnLockCommand)
    // ----------------------------------------------------------
    public String getUnlockCommand(String deviceNumber,
                                   String bleName,
                                   String power,
                                   String randomFactor) {

        String token = auth.getToken();

        System.out.println("Device number:"+deviceNumber+", BleName:"+bleName+", Power:"+power+", random Factor:"+randomFactor);

        Map<String, Object> body = Map.of(
                "appID", appId,
                "deviceNumber", deviceNumber,
                "deviceBleName", bleName,
                "devicePower", power,
                "deviceRandomFactor", randomFactor
        );

        String url = baseUrl + "/API/Device/DeviceUnLockCommand";
        System.out.println("📡 Enviando a: " + url+"token:"+token);

        WelockResponse resp = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(WelockResponse.class)
                .block();

        if (resp == null || resp.getCode() != 0) {
            throw new RuntimeException("Error obteniendo comando BLE: " + resp);
        }

        return resp.getData().toString();
    }
    // ----------------------------------------------------------
// COMANDO SYNC TIME BLE (DeviceSyncTime)
// ----------------------------------------------------------
    public String getSyncTimeCommand(String deviceNumber,
                                     String bleName,
                                     long timestamp,
                                     String randomFactor) {

        String token = auth.getToken();

        Map<String, Object> body = Map.of(
                "appID", appId,
                "deviceNumber", deviceNumber,
                "deviceBleName", bleName,
                "timestamp", timestamp,
                "deviceRandomFactor", randomFactor  // según docs puede ser "0000"
        );

        String url = baseUrl + "/API/Device/DeviceSyncTime";

        System.out.println("📡 SYNC TIME → Enviando a: " + url);

        WelockResponse resp = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(WelockResponse.class)
                .block();

        if (resp == null || resp.getCode() != 0) {
            throw new RuntimeException("Error obteniendo comando SyncTime: " + resp);
        }

        return resp.getData().toString();
    }

    // ----------------------------------------------------------
// GENERAR CONTRASEÑA TEMPORAL (GeneratePwd)
// ----------------------------------------------------------
    public String generateTempPassword(String deviceNumber,
                                       String bleName,
                                       String startDateTime,
                                       String endDateTime,
                                       int tempType) {

        String token = auth.getToken();

        Map<String, Object> body = Map.of(
                "appID", appId,
                "deviceNumber", deviceNumber,
                "deviceBleName", bleName,
                "startingTime", startDateTime,
                "endTime", endDateTime,
                "tempType", tempType  // 0 = continuo, 1 = por horario
        );

        String url = baseUrl + "/API/Device/DeviceTempPassword";
        System.out.println("📡 GENERATE TEMP PASSWORD → Enviando a: " + url);

        WelockResponse resp = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(WelockResponse.class)
                .block();

        if (resp == null || resp.getCode() != 0) {
            throw new RuntimeException("Error generando contraseña temporal: " + resp);
        }

        return resp.getData().toString();  // Esta es la contraseña
    }


}

