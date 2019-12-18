package c8y.example.cookbook.util;

import com.cumulocity.sdk.client.ParamSource;
import com.cumulocity.sdk.client.inventory.InventoryFilter;

public class ExtendedInventoryFilter extends InventoryFilter {
	
	@ParamSource
    private String query;

	public ExtendedInventoryFilter byQuery(String query) {
        this.query = query;
        return this;
    }

}
