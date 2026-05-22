package dag.usi.ch;

public class LongCounter {
    private long count = 0;

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
