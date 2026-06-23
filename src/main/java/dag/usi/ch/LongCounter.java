package dag.usi.ch;

public class LongCounter {
    private long count;

    public LongCounter(long c){
        count=c;
    }

    public void increment(){
        count++;
    }


    public long count(){
        return count;
    }
}
