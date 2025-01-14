/*
 * Copyright (C) 2010-2023, Danilo Pianini and contributors
 * listed, for each module, in the respective subproject's build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */
package it.unibo.alchemist.model.timedistributions;

import it.unibo.alchemist.model.times.DoubleTime;
import it.unibo.alchemist.model.Environment;
import it.unibo.alchemist.model.Node;
import it.unibo.alchemist.model.Time;

import javax.annotation.Nonnull;
import java.io.Serial;

/**
 * @param <T>
 *            Concentration type
 */
public final class Trigger<T> extends AbstractDistribution<T> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param event
     *            the time at which the event will happen
     */
    public Trigger(final Time event) {
        super(event);
    }

    @Override
    public double getRate() {
        return Double.NaN;
    }

    @Override
    protected void updateStatus(
            final Time currentTime,
            final boolean executed,
            final double param,
            final Environment<T, ?> environment
    ) {

        if (executed) {
            setNextOccurrence(new DoubleTime(Double.POSITIVE_INFINITY));
        }
    }

    @Override
    public Trigger<T> cloneOnNewNode(final @Nonnull Node<T> destination, final @Nonnull Time currentTime) {
        return new Trigger<>(getNextOccurence());
    }

}
