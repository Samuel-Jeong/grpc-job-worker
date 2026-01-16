package com.telcoware.job_worker_app_demo.controller.ha;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * packageName    : com.sks.wpm.core.ha
 * fileName       : HAController
 * author         : samuel
 * date           : 25. 3. 19.
 * description    : HA 컨트롤러
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 3. 19.        samuel       최초 생성
 */
@RestController
@RequestMapping("/ha")
public class HAController {

    @GetMapping(value = "")
    public ResponseEntity<String> ha() {
        return ResponseEntity.ok(HttpStatus.OK.name());
    }

}
