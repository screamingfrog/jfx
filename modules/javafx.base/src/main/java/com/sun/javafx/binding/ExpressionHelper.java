/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javafx.binding;

import java.util.LinkedHashSet;
import java.util.Set;

import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * A convenience class for creating implementations of {@link javafx.beans.value.ObservableValue}.
 * It contains all of the infrastructure support for value invalidation- and
 * change event notification.
 *
 * This implementation can handle adding and removing listeners while the
 * observers are being notified, but it is not thread-safe.
 *
 *
 */
public abstract class ExpressionHelper<T> extends ExpressionHelperBase {

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Static methods

    public static <T> ExpressionHelper<T> addListener(ExpressionHelper<T> helper, ObservableValue<T> observable, InvalidationListener listener) {
        if ((observable == null) || (listener == null)) {
            throw new NullPointerException();
        }
        observable.getValue(); // validate observable
        return (helper == null)? new SingleInvalidation<T>(observable, listener) : helper.addListener(listener);
    }

    public static <T> ExpressionHelper<T> removeListener(ExpressionHelper<T> helper, InvalidationListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        return (helper == null)? null : helper.removeListener(listener);
    }

    public static <T> ExpressionHelper<T> addListener(ExpressionHelper<T> helper, ObservableValue<T> observable, ChangeListener<? super T> listener) {
        if ((observable == null) || (listener == null)) {
            throw new NullPointerException();
        }
        return (helper == null)? new SingleChange<T>(observable, listener) : helper.addListener(listener);
    }

    public static <T> ExpressionHelper<T> removeListener(ExpressionHelper<T> helper, ChangeListener<? super T> listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        return (helper == null)? null : helper.removeListener(listener);
    }

    public static <T> void fireValueChangedEvent(ExpressionHelper<T> helper) {
        if (helper != null) {
            helper.fireValueChangedEvent();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Common implementations

    protected final ObservableValue<T> observable;

    private ExpressionHelper(ObservableValue<T> observable) {
        this.observable = observable;
    }

    protected abstract ExpressionHelper<T> addListener(InvalidationListener listener);
    protected abstract ExpressionHelper<T> removeListener(InvalidationListener listener);

    protected abstract ExpressionHelper<T> addListener(ChangeListener<? super T> listener);
    protected abstract ExpressionHelper<T> removeListener(ChangeListener<? super T> listener);

    protected abstract void fireValueChangedEvent();

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Implementations

    private static class SingleInvalidation<T> extends ExpressionHelper<T> {

        private final InvalidationListener listener;

        private SingleInvalidation(ObservableValue<T> expression, InvalidationListener listener) {
            super(expression);
            this.listener = listener;
        }

        @Override
        protected ExpressionHelper<T> addListener(InvalidationListener listener) {
            return new Generic<T>(observable, this.listener, listener);
        }

        @Override
        protected ExpressionHelper<T> removeListener(InvalidationListener listener) {
            return (listener.equals(this.listener))? null : this;
        }

        @Override
        protected ExpressionHelper<T> addListener(ChangeListener<? super T> listener) {
            return new Generic<T>(observable, this.listener, listener);
        }

        @Override
        protected ExpressionHelper<T> removeListener(ChangeListener<? super T> listener) {
            return this;
        }

        @Override
        protected void fireValueChangedEvent() {
            try {
                listener.invalidated(observable);
            } catch (Exception e) {
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
        }
    }

    private static class SingleChange<T> extends ExpressionHelper<T> {

        private final ChangeListener<? super T> listener;
        private T currentValue;

        private SingleChange(ObservableValue<T> observable, ChangeListener<? super T> listener) {
            super(observable);
            this.listener = listener;
            this.currentValue = observable.getValue();
        }

        @Override
        protected ExpressionHelper<T> addListener(InvalidationListener listener) {
            return new Generic<T>(observable, listener, this.listener);
        }

        @Override
        protected ExpressionHelper<T> removeListener(InvalidationListener listener) {
            return this;
        }

        @Override
        protected ExpressionHelper<T> addListener(ChangeListener<? super T> listener) {
            return new Generic<T>(observable, this.listener, listener);
        }

        @Override
        protected ExpressionHelper<T> removeListener(ChangeListener<? super T> listener) {
            return (listener.equals(this.listener))? null : this;
        }

        @Override
        protected void fireValueChangedEvent() {
            final T oldValue = currentValue;
            currentValue = observable.getValue();
            final boolean changed = (currentValue == null)? (oldValue != null) : !currentValue.equals(oldValue);
            if (changed) {
                try {
                    listener.changed(observable, oldValue, currentValue);
                } catch (Exception e) {
                    Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
                }
            }
        }
    }

    private static class Generic<T> extends ExpressionHelper<T> {

        private Set<InvalidationListener> invalidationListeners = new LinkedHashSet<>();
        private Set<ChangeListener<? super T>> changeListeners = new LinkedHashSet<>();
        private T currentValue;
        private int weakChangeListenerGcCount = 2;
        private int weakInvalidationListenerGcCount = 2;

        private Generic(ObservableValue<T> observable, InvalidationListener listener0, InvalidationListener listener1) {
            super(observable);
            this.invalidationListeners.add(listener0);
            this.invalidationListeners.add(listener1);
        }

        private Generic(ObservableValue<T> observable, ChangeListener<? super T> listener0, ChangeListener<? super T> listener1) {
            super(observable);
            this.changeListeners.add(listener0);
            this.changeListeners.add(listener1);
            this.currentValue = observable.getValue();
        }

        private Generic(ObservableValue<T> observable, InvalidationListener invalidationListener, ChangeListener<? super T> changeListener) {
            super(observable);
            this.invalidationListeners.add(invalidationListener);
            this.changeListeners.add(changeListener);
            this.currentValue = observable.getValue();
        }

        @Override
        protected Generic<T> addListener(InvalidationListener listener) {
            if (invalidationListeners.size() == weakInvalidationListenerGcCount) {
                removeWeakListeners(invalidationListeners);
                if (invalidationListeners.size() == weakInvalidationListenerGcCount) {
                    weakInvalidationListenerGcCount = (weakInvalidationListenerGcCount * 3)/2 + 1;
                }
            }
            invalidationListeners.add(listener);
            return this;
        }

        @Override
        protected ExpressionHelper<T> removeListener(InvalidationListener listener) {
            if (invalidationListeners.remove(listener)) {
                if (invalidationListeners.isEmpty() && changeListeners.size() == 1) {
                    return new SingleChange<T>(observable, changeListeners.iterator().next());
                } else if ((invalidationListeners.size() == 1) && changeListeners.isEmpty()) {
                    return new SingleInvalidation<T>(observable, invalidationListeners.iterator().next());
                }
            }
            return this;
        }

        @Override
        protected ExpressionHelper<T> addListener(ChangeListener<? super T> listener) {
            if (changeListeners.size() == weakChangeListenerGcCount) {
                removeWeakListeners(changeListeners);
                if (changeListeners.size() == weakChangeListenerGcCount) {
                    weakChangeListenerGcCount = (weakChangeListenerGcCount * 3)/2 + 1;
                }
            }
            changeListeners.add(listener);

            if (changeListeners.size() == 1) {
                currentValue = observable.getValue();
            }
            return this;
        }

        @Override
        protected ExpressionHelper<T> removeListener(ChangeListener<? super T> listener) {
            if (changeListeners.remove(listener)) {
                if (changeListeners.isEmpty() && invalidationListeners.size() == 1) {
                    return new SingleInvalidation<T>(observable, invalidationListeners.iterator().next());
                } else if ((changeListeners.size() == 1) && invalidationListeners.isEmpty()) {
                    return new SingleChange<T>(observable, changeListeners.iterator().next());
                }
            }
            return this;
        }

        @Override
        protected void fireValueChangedEvent() {
            final Set<InvalidationListener> curInvalidationList = new LinkedHashSet<>(invalidationListeners);
            final Set<ChangeListener<? super T>> curChangeList = new LinkedHashSet<>(changeListeners);

            curInvalidationList.forEach(listener -> {
                try {
                    listener.invalidated(observable);
                } catch (Exception e) {
                    Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(
                        Thread.currentThread(), e);
                }
            });
            if (!curChangeList.isEmpty()) {
                final T oldValue = currentValue;
                currentValue = observable.getValue();
                final boolean changed = (currentValue == null)? (oldValue != null) : !currentValue.equals(oldValue);
                if (changed) {
                    curChangeList.forEach(listener -> {
                        try {
                            listener.changed(observable, oldValue, currentValue);
                        } catch (Exception e) {
                            Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(
                                Thread.currentThread(), e);
                        }
                    });
                }
            }
        }
    }
}
