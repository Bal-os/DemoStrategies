package velox.api.layer1.simplified.demo;

import java.awt.Color;

import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1SimpleAttachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import velox.api.layer1.simplified.Api;
import velox.api.layer1.simplified.CustomModule;
import velox.api.layer1.simplified.Indicator;
import velox.api.layer1.simplified.TradeDataListener;

@Layer1SimpleAttachable
@Layer1StrategyName("Last trade: live")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION1)
public class LastTradeDemoNoHistory implements CustomModule, TradeDataListener
{
    private Indicator lastTradeIndicator;

    @Override
    public void initialize(String alias, InstrumentInfo info, Api api) {
        lastTradeIndicator = api.registerIndicator("Last trade, no history",
                GraphType.PRIMARY, Color.GREEN);
    }
    
    @Override
    public void stop() {
    }

    @Override
    public void onTrade(double price, int size, TradeInfo tradeInfo) {
        lastTradeIndicator.addPoint(price + 2);
    }
}
