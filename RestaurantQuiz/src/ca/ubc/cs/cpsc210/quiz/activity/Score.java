package ca.ubc.cs.cpsc210.quiz.activity;

/**
 * Created by pcarter on 14-11-06.
 *
 * Represents a score consisting of number of attempts taken to find restaurants
 * and points earned.
 */
public class Score {
    int numAttempts;
    int pointsEarned;

    /**
     * Constructor initializes score with given number of attempts and points earned
     *
     * @param numAttempts   number of attempts taken to guess location of restaurant
     * @param pointsEarned  number of points earned
     */
    public Score(int numAttempts, int pointsEarned) {
        this.numAttempts = numAttempts;
        this.pointsEarned = pointsEarned;
    }

    public int getPointsEarned() {
        return this.pointsEarned;
    }

    public int getNumAttempts() {
        return this.numAttempts;
    }
}
