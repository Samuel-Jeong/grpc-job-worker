package com.telcoware.job_worker_app_demo.scheduler.job;

/**
 * packageName    : com.capshome.iotgw.scheduler.job
 * fileName       : JobContainer
 * author         : samuel
 * date           : 24. 8. 5.
 * description    : 작업 컨테이너 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 24. 8. 5.        samuel       최초 생성
 */
public abstract class JobContainer {

    private Job job = null;

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

}
