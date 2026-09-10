package randomTeamFC27.service.otp;

import randomTeamFC27.entity.OtpChannel;

/**
 * Delivers a one-time code to a destination over the given channel. The mock/log-only
 * implementation is the only one wired up for now — a real email/SMS provider can be
 * swapped in later behind this same interface without touching any caller.
 */
public interface OtpSender {

    void send(String destination, OtpChannel channel, String code);
}
