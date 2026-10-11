package com.sajuppugi.member.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MemberDeletionJob {
    private final MemberService members;

    public MemberDeletionJob(MemberService members) {
        this.members = members;
    }

    @Scheduled(cron = "${app.member.deletion-cron:0 20 3 * * *}", zone = "Asia/Seoul")
    public void purgeDueWithdrawals() {
        members.purgeDueWithdrawals();
    }
}
