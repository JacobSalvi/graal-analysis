package dag.usi.ch;

import java.io.IOException;
import java.util.Iterator;

public abstract class AbstractPerfStream implements Iterator<PerfInfo>, AutoCloseable {


    public AbstractPerfStream() {
    }


    @Override
    abstract public boolean hasNext();

    @Override
    abstract public PerfInfo next();

    abstract public void skip(int n);

    @Override
    public abstract void close() throws IOException;
}
