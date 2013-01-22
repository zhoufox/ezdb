package ezleveldb.hashrange;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;

import ezleveldb.HashRangeComparator;
import ezleveldb.serde.Serde;

public class LevelDbHashRangeTable<H, R, V> implements HashRangeTable<H, R, V> {
  private final DB db;
  private final Serde<H> hashKeySerde;
  private final Serde<R> rangeKeySerde;
  private final Serde<V> valueSerde;

  public LevelDbHashRangeTable(File path, Serde<H> hashKeySerde, Serde<R> rangeKeySerde, Serde<V> valueSerde) throws IOException {
    Options options = new Options();
    options.createIfMissing(true);
    options.comparator(new HashRangeComparator());
    this.db = JniDBFactory.factory.open(path, options);
    this.hashKeySerde = hashKeySerde;
    this.rangeKeySerde = rangeKeySerde;
    this.valueSerde = valueSerde;
  }

  @Override
  public void put(H hashKey, V value) {
    put(hashKey, null, value);
  }

  @Override
  public void put(H hashKey, R rangeKey, V value) {
    db.put(combine(hashKey, rangeKey), valueSerde.toBytes(value));
  }

  @Override
  public V get(H hashKey) {
    return get(hashKey, null);
  }

  @Override
  public V get(H hashKey, R rangeKey) {
    return valueSerde.fromBytes(db.get(combine(hashKey, rangeKey)));
  }

  @Override
  public TableIterator<H, R, V> range(H hashKey) {
    final DBIterator iterator = db.iterator();
    final byte[] keyBytesFrom = combine(hashKey, null);
    iterator.seek(keyBytesFrom);
    return new TableIterator<H, R, V>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext() && HashRangeComparator.compareKeys(keyBytesFrom, iterator.peekNext().getKey(), false) == 0;
      }

      @Override
      public TableRow<H, R, V> next() {
        return new LevelDbHashRangeTableRow<H, R, V>(iterator.next(), hashKeySerde, rangeKeySerde, valueSerde);
      }

      @Override
      public void remove() {
        iterator.remove();
      }

      @Override
      public void close() {
        iterator.close();
      }
    };
  }

  @Override
  public TableIterator<H, R, V> range(H hashKey, R fromRangeKey, R toRangeKey) {
    final DBIterator iterator = db.iterator();
    final byte[] keyBytesFrom = combine(hashKey, fromRangeKey);
    final byte[] keyBytesTo = combine(hashKey, toRangeKey);
    iterator.seek(keyBytesFrom);
    return new TableIterator<H, R, V>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext() && HashRangeComparator.compareKeys(keyBytesFrom, iterator.peekNext().getKey(), true) <= 0 && HashRangeComparator.compareKeys(keyBytesTo, iterator.peekNext().getKey(), true) >= 0;
      }

      @Override
      public TableRow<H, R, V> next() {
        return new LevelDbHashRangeTableRow<H, R, V>(iterator.next(), hashKeySerde, rangeKeySerde, valueSerde);
      }

      @Override
      public void remove() {
        iterator.remove();
      }

      @Override
      public void close() {
        iterator.close();
      }
    };
  }

  @Override
  public void delete(H hashKey) {
    delete(hashKey, null);
  }

  @Override
  public void delete(H hashKey, R rangeKey) {
    db.delete(combine(hashKey, rangeKey));
  }

  @Override
  public void close() {
    this.db.close();
  }

  private byte[] combine(H hashKey, R rangeKey) {
    byte[] rangeBytes = new byte[0];

    if (rangeKey != null) {
      rangeBytes = rangeKeySerde.toBytes(rangeKey);
    }

    return combine(hashKeySerde.toBytes(hashKey), rangeBytes);
  }

  public static byte[] combine(byte[] arg1, byte[] arg2) {
    byte[] result = new byte[8 + arg1.length + arg2.length];
    System.arraycopy(ByteBuffer.allocate(4).putInt(arg1.length).array(), 0, result, 0, 4);
    System.arraycopy(arg1, 0, result, 4, arg1.length);
    System.arraycopy(ByteBuffer.allocate(4).putInt(arg2.length).array(), 0, result, 4 + arg1.length, 4);
    System.arraycopy(arg2, 0, result, 8 + arg1.length, arg2.length);
    return result;
  }
}