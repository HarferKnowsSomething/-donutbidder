package dev.donut.bidder.auction;

/**
 * One payment recorded during an auction.
 *
 * @param player    the payer's username
 * @param amount    the size of that single payment
 * @param total     the player's running total at the time of this payment
 * @param timestamp system time of the payment
 * @param accepted  false when the payment did not beat the current highest bid
 */
public record Bid(String player, double amount, double total, long timestamp, boolean accepted) {
}
