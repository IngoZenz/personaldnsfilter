package util;

import java.util.Iterator;
import java.util.Vector;

public class PatternSequence {

    private class Pattern {
        private String[] pattern;
        private Object value;
        private int hashcode;
        private Pattern(String patternString, Object value){
            this.pattern = patternString.split("\\*", -1);
            this.value = value;
            hashcode = patternString.hashCode() + value.hashCode();
        }

        @Override
        public int hashCode() {
            return hashcode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj != null && hashcode == obj.hashCode() && obj instanceof Pattern)
                return patternEqual((Pattern) obj);
            else
                return false;
        }

        private boolean patternEqual(Pattern overrulePattern) {
            if (pattern.length == overrulePattern.pattern.length && overrulePattern.value.equals(value)) {
                for (int i = 0; i < pattern.length; i++) {
                    if (!pattern[i].equals(overrulePattern.pattern[i]))
                        return false;
                }
                return true;
            } else
                return false;
        }

        public Object match(String s) {

            // Iterate over the parts.
            for (int i = 0; i < pattern.length; i++) {
                String part = pattern[i];

                int idx = -1;
                if (i < pattern.length-1)
                    idx = s.indexOf(part);
                else
                    idx = s.lastIndexOf(part);

                if (i == 0 && !part.equals("") && idx != 0) {
                    // i == 0 ==> we are on the first fixed part
                    // first fixed part is not empty ==> Matching String must start with first fixed part
                    // if not, no match!
                    return null;
                }
                if (i == pattern.length-1 && !part.equals("") && idx + part.length() != s.length()) {
                    // i == last part
                    // last part is not empty ==> Matching String must end with last part
                    // if not, no match
                    return null;
                }
                // part not detected in the text.
                if (idx == -1) {
                    return null;
                }
                // Move ahead, towards the right of the text.
                s = s.substring(idx + part.length());
            }
            return value;
        }

    }

    private static final Object NULL = new Object();
    Vector<Pattern> patternList = new Vector<Pattern>();
    final LRUCache matchedPatternCache = new LRUCache(1000);

    public synchronized boolean addPattern(String pattern, Object value) {
        synchronized (matchedPatternCache) {
            matchedPatternCache.clear();
        }
        return patternList.add(new Pattern(pattern, value));
    }

    public synchronized boolean removePattern(String pattern, Object value) {
        synchronized (matchedPatternCache) {
            matchedPatternCache.clear();
        }
        return patternList.remove(new Pattern(pattern, value));
    }

    public Object match(String s) {

        Object value;
        synchronized (matchedPatternCache) {
            value = matchedPatternCache.get(s);
        }
        if (value == NULL)
            return null;
        if (value != null)
            return value;

        Pattern[] snapshot;
        synchronized (this) {
            snapshot = patternList.toArray(new Pattern[0]);
        }

        for (int i = 0; i < snapshot.length; i++) {
            value = snapshot[i].match(s);
            if (value != null) {
                synchronized (matchedPatternCache) {
                    matchedPatternCache.put(s, value);
                }
                return value;
            }
        }
        synchronized (matchedPatternCache) {
            matchedPatternCache.put(s, NULL);
        }
        return null;
    }

    public synchronized void clear() {
        patternList.clear();
        synchronized (matchedPatternCache) {
            matchedPatternCache.clear();
        }
    }


}
