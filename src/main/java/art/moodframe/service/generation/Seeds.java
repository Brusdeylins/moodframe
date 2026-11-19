package art.moodframe.service.generation;

import java.security.SecureRandom;

/**
 * Random seeds for the images of a provider that {@link art.moodframe.service.provider.ImageProvider#usesSeeds() uses
 * seeds}, when the user gives none.
 *
 * @since 2.1.3
 */
final class Seeds {

    private static final SecureRandom random = new SecureRandom();

    private Seeds() {
    }

    /**
     * Gets a strong random seed, never 0 (which might be taken as "no seed"). Several entropy sources are combined, so
     * the images of one request get different seeds.
     *
     * @param imageIndex the index of the image in the request
     * @return the seed
     */
    static long random(int imageIndex) {
        long timeSeed = System.nanoTime();
        long indexSeed = (long) imageIndex * 982451653L;  // a large prime for the index
        long threadSeed = (long) Thread.currentThread().hashCode() * 1009L;
        long seed = timeSeed ^ random.nextLong() ^ random.nextLong() ^ indexSeed ^ threadSeed
                ^ Runtime.getRuntime().freeMemory();
        while (seed == 0)
            seed = random.nextLong();
        return seed;
    }
}
