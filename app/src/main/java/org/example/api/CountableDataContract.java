package org.example.api;

import java.util.List;

public interface CountableDataContract<T> {
    int totalCount = 0;

    int getTotalCount();
    void setTotalCount(int totalCount);
    List<T> getItems();
}
