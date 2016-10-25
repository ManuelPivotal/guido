package oss.guido.com.lmax.disruptor.dsl;

import java.util.concurrent.Executor;

import oss.guido.com.lmax.disruptor.Sequence;
import oss.guido.com.lmax.disruptor.SequenceBarrier;

interface ConsumerInfo
{
    Sequence[] getSequences();

    SequenceBarrier getBarrier();

    boolean isEndOfChain();

    void start(Executor executor);

    void halt();

    void markAsUsedInBarrier();

    boolean isRunning();
}
