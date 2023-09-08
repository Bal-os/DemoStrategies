package velox.api.layer1.simpledemo.markers;

import velox.api.layer1.common.Log;
import velox.api.layer1.data.*;
import velox.api.layer1.datastructure.events.OrderExecutedEvent;
import velox.api.layer1.datastructure.events.OrderUpdatedEvent;
import velox.api.layer1.datastructure.events.OrderUpdatesExecutionsAggregationEvent;
import velox.api.layer1.datastructure.events.TradeAggregationEvent;
import velox.api.layer1.layers.strategies.interfaces.CalculatedResultListener;
import velox.api.layer1.layers.strategies.interfaces.InvalidateInterface;
import velox.api.layer1.layers.strategies.interfaces.OnlineCalculatable;
import velox.api.layer1.layers.strategies.interfaces.OnlineValueCalculatorAdapter;
import velox.api.layer1.messages.indicators.DataStructureInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MarkersOnlineCalculator implements OnlineCalculatable {
    private final Map<String, Double> pipsMap = new ConcurrentHashMap<>();
    private final MarkersIndicatorColor markersIndicatorColor;
    private final Layer1ApiMarkersDemo layer1ApiMarkersDemo;
    private DataStructureInterface dataStructureInterface;

    public MarkersOnlineCalculator(MarkersIndicatorColor markersIndicatorColor,
                                   Layer1ApiMarkersDemo layer1ApiMarkersDemo) {
        this.markersIndicatorColor = markersIndicatorColor;
        this.layer1ApiMarkersDemo = layer1ApiMarkersDemo;
    }

    @Override
    public void calculateValuesInRange(String indicatorName, String alias, long t0, long intervalWidth,
                                       int intervalsNumber, CalculatedResultListener listener) {
        if (dataStructureInterface == null) {
            listener.setCompleted();
            return;
        }

        String userName = layer1ApiMarkersDemo.getFullNameByIndicator(indicatorName);

        switch (userName) {
            case Layer1ApiMarkersDemo.INDICATOR_NAME_TRADE: {
                calculateTradeValueInRange(alias, t0, intervalWidth, intervalsNumber, listener);
                break;
            }
            case Layer1ApiMarkersDemo.INDICATOR_NAME_CIRCLES: {
                calculateCirclesValueInRange(alias, t0, intervalWidth, intervalsNumber, listener);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown indicator name " + indicatorName);
        }

    }

    private void calculateCirclesValueInRange(String alias, long t0, long intervalWidth, int intervalsNumber, CalculatedResultListener listener) {
        ArrayList<DataStructureInterface.TreeResponseInterval> intervalResponse = dataStructureInterface.get(t0, intervalWidth, intervalsNumber, alias,
                new DataStructureInterface.StandardEvents[]{DataStructureInterface.StandardEvents.ORDER});
        for (int i = 1; i <= intervalsNumber; ++i) {
            OrderUpdatesExecutionsAggregationEvent orders = (OrderUpdatesExecutionsAggregationEvent) intervalResponse.get(i).events.get(DataStructureInterface.StandardEvents.ORDER.toString());

            ArrayList<Marker> result = new ArrayList<>();

            for (Object object : orders.orderUpdates) {
                if (object instanceof OrderExecutedEvent) {
                    OrderExecutedEvent orderExecutedEvent = (OrderExecutedEvent) object;
                    result.add(markersIndicatorColor.createNewTradeMarker(
                            orderExecutedEvent.executionInfo.price / pipsMap.getOrDefault(orderExecutedEvent.alias, 1.)));
                } else if (object instanceof OrderUpdatedEvent) {
                    OrderUpdatedEvent orderUpdatedEvent = (OrderUpdatedEvent) object;
                    if (orderUpdatedEvent.orderInfoUpdate.status == OrderStatus.CANCELLED) {
                        result.add(markersIndicatorColor.createNewOrderMarker(
                                getActivePrice(orderUpdatedEvent.orderInfoUpdate) / pipsMap.getOrDefault(orderUpdatedEvent.alias, 1.),
                                alias));
                    }
                }
            }

            listener.provideResponse(result);
        }

        listener.setCompleted();
    }

    @Override
    public OnlineValueCalculatorAdapter createOnlineValueCalculator(String indicatorName,
                                                                    String indicatorAlias,
                                                                    long time,
                                                                    Consumer<Object> listener,
                                                                    InvalidateInterface invalidateInterface) {
        String userName = layer1ApiMarkersDemo.getFullNameByIndicator(indicatorName);
        layer1ApiMarkersDemo.putInvalidateInterface(userName, invalidateInterface);

        if (dataStructureInterface == null) {
            return new OnlineValueCalculatorAdapter() {
            };
        }

        switch (userName) {
            case Layer1ApiMarkersDemo.INDICATOR_NAME_TRADE:
                return getTradeOnlineValueCalculatorAdapter(indicatorAlias, listener);
            case Layer1ApiMarkersDemo.INDICATOR_NAME_CIRCLES:
                return getCirclesOnlineValueCalculatorAdapter(indicatorAlias, listener);
            default:
                throw new IllegalArgumentException("Unknown indicator name " + indicatorName);
        }
    }

    public void putPipsByAlias(String alias, double pips) {
        pipsMap.put(alias, pips);
    }

    public void setDataStructureInterface(DataStructureInterface dataStructureInterface) {
        this.dataStructureInterface = dataStructureInterface;
    }

    private void calculateTradeValueInRange(String alias, long t0, long intervalWidth, int intervalsNumber, CalculatedResultListener listener) {
        ArrayList<DataStructureInterface.TreeResponseInterval> intervalResponse =
                dataStructureInterface.get(t0, intervalWidth, intervalsNumber, alias,
                        new DataStructureInterface.StandardEvents[]{DataStructureInterface.StandardEvents.TRADE});

        double lastPrice = ((TradeAggregationEvent) intervalResponse.get(0).events
                .get(DataStructureInterface.StandardEvents.TRADE.toString())).lastPrice;

        for (int i = 1; i <= intervalsNumber; ++i) {
            TradeAggregationEvent trades = (TradeAggregationEvent) intervalResponse.get(i).events
                    .get(DataStructureInterface.StandardEvents.TRADE.toString());

            if (!Double.isNaN(trades.lastPrice)) {
                lastPrice = trades.lastPrice;
            }

            if (trades.askAggressorMap.isEmpty() && trades.bidAggressorMap.isEmpty()) {
                listener.provideResponse(lastPrice);
            } else {
                listener.provideResponse(markersIndicatorColor.createNewTradeMarker(lastPrice));
            }
        }

        listener.setCompleted();
    }

    private OnlineValueCalculatorAdapter getCirclesOnlineValueCalculatorAdapter(String indicatorAlias,
                                                                                Consumer<Object> listener) {
        return new OnlineValueCalculatorAdapter() {
            private final Map<String, String> orderIdToAlias = new HashMap<>();

            @Override
            public void onOrderExecuted(ExecutionInfo executionInfo) {
                String alias = orderIdToAlias.get(executionInfo.orderId);
                if (alias != null) {
                    if (alias.equals(indicatorAlias)) {
                        Double pips = pipsMap.get(alias);
                        if (pips != null) {
                            listener.accept(markersIndicatorColor
                                    .createNewOrderMarker(executionInfo.price / pips, alias));
                        } else {
                            Log.info("Unknown pips for instrument " + alias);
                        }

                    }
                } else {
                    Log.warn("Markers demo: Unknown alias for execution with order id " + executionInfo.orderId);
                }
            }

            @Override
            public void onOrderUpdated(OrderInfoUpdate orderInfoUpdate) {
                if (orderInfoUpdate.instrumentAlias.equals(indicatorAlias)) {
                    if (orderInfoUpdate.status == OrderStatus.CANCELLED ||
                            orderInfoUpdate.status == OrderStatus.DISCONNECTED) {
                        Double pips = pipsMap.get(orderInfoUpdate.instrumentAlias);
                        if (pips != null) {
                            listener.accept(markersIndicatorColor
                                    .createNewOrderMarker(getActivePrice(orderInfoUpdate) / pips, indicatorAlias));
                        } else {
                            Log.info("Unknown pips for instrument " + orderInfoUpdate.instrumentAlias);
                        }
                    }
                }
                orderIdToAlias.put(orderInfoUpdate.orderId, orderInfoUpdate.instrumentAlias);
            }
        };
    }

    private OnlineValueCalculatorAdapter getTradeOnlineValueCalculatorAdapter(String indicatorAlias,
                                                                              Consumer<Object> listener) {
        return new OnlineValueCalculatorAdapter() {
            @Override
            public void onTrade(String alias, double price, int size, TradeInfo tradeInfo) {
                if (alias.equals(indicatorAlias)) {
                    listener.accept(markersIndicatorColor.createNewTradeMarker(price));
                }
            }
        };
    }

    private double getActivePrice(OrderInfoUpdate orderInfoUpdate) {
        return (orderInfoUpdate.type == OrderType.STP || orderInfoUpdate.type == OrderType.STP_LMT)
                ? orderInfoUpdate.stopPrice : orderInfoUpdate.limitPrice;
    }
}
