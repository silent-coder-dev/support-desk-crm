package com.support.crm.service;


import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseNotificationService {

    // Browser EventSource reconnects by itself, so dead connections can safely expire
    private static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L;

    // Jitne bhi active browsers connect honge, unki list
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // Jab browser live stream se connect karega
    public SseEmitter subscribe(){
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitters.remove(emitter));

        // Pehla message turant bhejo taaki proxy connection ko open maan le
        try{
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    // Jab ticket mein kuch badalna ho toh sabhi connected agents ko signal bhejo.
    // Sirf ticket ID jaati hai; browser details secured page reload karke laata hai.
    @Async // Ek slow browser kisi agent ki request ko block nahi karega
    public void broadcast(String eventType, String ticketId){
        for(SseEmitter emitter : emitters){
            try{
                emitter.send(SseEmitter.event().name(eventType).data(ticketId));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

}
