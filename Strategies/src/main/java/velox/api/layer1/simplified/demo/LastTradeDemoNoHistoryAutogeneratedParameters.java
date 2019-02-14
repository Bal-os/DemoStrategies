package velox.api.layer1.simplified.demo;

import java.awt.Color;

import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1SimpleAttachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import velox.api.layer1.simplified.Api;
import velox.api.layer1.simplified.CustomModule;
import velox.api.layer1.simplified.Indicator;
import velox.api.layer1.simplified.InitialState;
import velox.api.layer1.simplified.Parameter;
import velox.api.layer1.simplified.ParameterChangeListener;
import velox.api.layer1.simplified.TradeDataListener;

@Layer1SimpleAttachable
@Layer1StrategyName("Last trade: live (with parameters)")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class LastTradeDemoNoHistoryAutogeneratedParameters implements CustomModule, TradeDataListener, ParameterChangeListener {
    private Indicator lastTradeIndicator;
    
    @Parameter(name="Line color")
    Color lineColor = Color.ORANGE;

    @Parameter(name = "Price shift", step = 1.0)
    Double priceShift = 2.0;
    
    @Parameter(name = "Sample text", step = 0.0, reloadOnChange = true)
    String any = "Any";
    
    @Override
    public void initialize(String alias, InstrumentInfo info, Api api, InitialState initialState) {
        lastTradeIndicator = api.registerIndicator("Last trade, no history",
                GraphType.PRIMARY);
        lastTradeIndicator.setColor(lineColor);
    }
    
    @Override
    public void stop() {
    }

    @Override
    public void onTrade(double price, int size, TradeInfo tradeInfo) {
        lastTradeIndicator.addPoint(price + priceShift);
    }

    @Override
    public void onParameterChanged(String parameterName) {
        Log.info(parameterName + " has been changed");
    }

    
}
