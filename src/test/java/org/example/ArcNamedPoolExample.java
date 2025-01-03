package org.example;

public class ArcNamedPoolExample {
    public static void main(String[] args) {

    }

    public interface MarketData {
        long getSecurityId();

        double getBid();

        double getAsk();

        interface Builder {
            Builder setSecurityId(long securityId);

            Builder setBid(double bid);

            Builder setAsk(double ask);

            Builder clear();

            MarketData build();
        }
    }

    public static class MutableMarketData implements MarketData {
        private final MutableBuilder mutableBuilder = new MutableBuilder(this);
        private long securityId;
        private double bid;
        private double ask;

        private void clear() {
            securityId = 0;
            bid = Double.NaN;
            ask = Double.NaN;
        }

        @Override
        public long getSecurityId() {
            return securityId;
        }

        @Override
        public double getBid() {
            return bid;
        }

        @Override
        public double getAsk() {
            return ask;
        }

        public MutableBuilder asMutable() {
            return mutableBuilder;
        }

        public static class MutableBuilder implements MarketData.Builder {
            private final MutableMarketData v;

            private MutableBuilder(MutableMarketData v) {
                this.v = v;
            }

            @Override
            public Builder setSecurityId(long securityId) {
                v.securityId = securityId;
                return this;
            }

            @Override
            public Builder setBid(double bid) {
                v.bid = bid;
                return this;
            }

            @Override
            public Builder setAsk(double ask) {
                v.ask = ask;
                return null;
            }

            @Override
            public Builder clear(){
                v.clear();
                return this;
            }

            @Override
            public MarketData build() {
                return v;
            }
        }
    }
}
