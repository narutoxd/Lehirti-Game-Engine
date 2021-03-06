package org.lehirti.state;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateObject implements Externalizable {
  private static final long serialVersionUID = 1L;
  
  private static final Logger LOGGER = LoggerFactory.getLogger(StateObject.class);
  
  public static final Random DIE = new Random();
  
  private static final StateObject INSTANCE = new StateObject();
  
  private static final Comparator<State> SORTER = new Comparator<State>() {
    
    @Override
    public int compare(final State o1, final State o2) {
      if (o1.getClass().equals(o2.getClass())) {
        return o1.name().compareTo(o2.name());
      }
      return o1.getClass().getName().compareTo(o2.getClass().getName());
    }
    
  };
  
  private final SortedMap<BoolState, Boolean> BOOL_MAP = new TreeMap<BoolState, Boolean>(SORTER);
  private final SortedMap<IntState, Integer> INT_MAP = new TreeMap<IntState, Integer>(SORTER);
  private final SortedMap<StringState, String> STRING_MAP = new TreeMap<StringState, String>(SORTER);
  
  // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // static getters for all state
  // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  public static boolean is(final BoolState key) {
    Boolean value = INSTANCE.BOOL_MAP.get(key);
    if (value == null) {
      value = key.defaultValue();
    }
    return value.booleanValue();
  }
  
  public static void set(final BoolState key, final boolean value) {
    INSTANCE.BOOL_MAP.put(key, Boolean.valueOf(value));
  }
  
  public static int get(final IntState key) {
    Integer value = INSTANCE.INT_MAP.get(key);
    if (value == null) {
      value = key.defaultValue();
    }
    return value.intValue();
  }
  
  public static void set(final IntState key, final int value) {
    INSTANCE.INT_MAP.put(key, Integer.valueOf(value));
  }
  
  public static String get(final StringState key) {
    String value = INSTANCE.STRING_MAP.get(key);
    if (value == null) {
      value = key.defaultValue();
    }
    return value;
  }
  
  public static void set(final StringState key, final String value) {
    INSTANCE.STRING_MAP.put(key, value);
  }
  
  // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Save/Load
  // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  public static void save(final ObjectOutputStream oos) {
    try {
      oos.writeObject(INSTANCE);
    } catch (final IOException e) {
      LOGGER.error("Unable to save state", e);
    }
  }
  
  public static void load(final ObjectInputStream ois) {
    try {
      ois.readObject();
    } catch (final IOException e) {
      LOGGER.error("Unable to load state", e);
    } catch (final ClassNotFoundException e) {
      LOGGER.error("Unable to load state", e);
    }
    
  }
  
  private static enum OnDiskDelim {
    
    START_BOOL_MAP,
    // there MUST NOT BE another constant between these two START/END constants
    END_BOOL_MAP,
    
    START_INT_MAP,
    // there MUST NOT BE another constant between these two START/END constants
    END_INT_MAP,
    
    START_STRING_MAP,
    // there MUST NOT BE another constant between these two START/END constants
    END_STRING_MAP,
    
    END_STATE_OBJECT;
    
    public OnDiskDelim getNext() {
      switch (this) {
      case START_BOOL_MAP:
        return END_BOOL_MAP;
      case START_INT_MAP:
        return END_INT_MAP;
      case START_STRING_MAP:
        return END_STRING_MAP;
      default:
        throw new RuntimeException("No clearly defined next delim for: " + this.name());
      }
    }
  }
  
  @Override
  public void writeExternal(final ObjectOutput out) throws IOException {
    out.writeLong(serialVersionUID);
    out.writeObject(OnDiskDelim.START_BOOL_MAP.name());
    writeMap(out, this.BOOL_MAP);
    out.writeObject(OnDiskDelim.END_BOOL_MAP.name());
    out.writeObject(OnDiskDelim.START_INT_MAP.name());
    writeMap(out, this.INT_MAP);
    out.writeObject(OnDiskDelim.END_INT_MAP.name());
    out.writeObject(OnDiskDelim.START_STRING_MAP.name());
    writeMap(out, this.STRING_MAP);
    out.writeObject(OnDiskDelim.END_STRING_MAP.name());
    out.writeObject(OnDiskDelim.END_STATE_OBJECT.name());
  }
  
  private void writeMap(final ObjectOutput out, final Map<? extends State, ? extends Object> map) throws IOException {
    for (final Map.Entry<? extends State, ? extends Object> entry : map.entrySet()) {
      out.writeObject(entry.getKey().getClass().getName());
      out.writeObject(entry.getKey().name());
      out.writeObject(entry.getValue());
    }
  }
  
  @Override
  public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
    final long version = in.readLong();
    if (version == 1L) {
      readObject1(in);
    } else {
      throw new RuntimeException("Unknown StateObject serialVersionUID: " + version);
    }
  }
  
  private void readObject1(final ObjectInput in) throws IOException, ClassNotFoundException {
    INSTANCE.BOOL_MAP.clear();
    INSTANCE.INT_MAP.clear();
    INSTANCE.STRING_MAP.clear();
    
    OnDiskDelim delim;
    while ((delim = OnDiskDelim.valueOf((String) in.readObject())) != OnDiskDelim.END_STATE_OBJECT) {
      readMap(in, delim);
    }
  }
  
  @SuppressWarnings("unchecked")
  private void readMap(final ObjectInput in, final OnDiskDelim startDelim) throws IOException, ClassNotFoundException {
    String className;
    final OnDiskDelim endDelim = startDelim.getNext();
    while (!endDelim.name().equals((className = (String) in.readObject()))) {
      final String keyName = (String) in.readObject();
      final Object value = in.readObject();
      
      try {
        final State key = (State) Enum.valueOf((Class<? extends Enum>) Class.forName(className), keyName);
        switch (startDelim) {
        case START_BOOL_MAP:
          INSTANCE.BOOL_MAP.put((BoolState) key, (Boolean) value);
          break;
        case START_INT_MAP:
          INSTANCE.INT_MAP.put((IntState) key, (Integer) value);
          break;
        case START_STRING_MAP:
          INSTANCE.STRING_MAP.put((StringState) key, (String) value);
          break;
        default:
          throw new RuntimeException("Unknown OnDiskDelim: " + startDelim.name());
        }
      } catch (final RuntimeException e) {
        LOGGER.warn("Ignoring unkown state {}.{}={}", new Object[] { className, keyName, value });
      }
    }
  }
}
