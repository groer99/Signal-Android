package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

import java.util.LinkedList;
import java.util.List;

public abstract class MappingViewHolder<Model extends MappingModel<Model>> extends LifecycleViewHolder implements LifecycleOwner {

  protected final Context      context;
  protected final List<Object> payload;

  public MappingViewHolder(@NonNull View itemView) {
    super(itemView);
    context = itemView.getContext();
    payload = new LinkedList<>();
  }

  public <T extends View> T findViewById(@IdRes int id) {
    return itemView.findViewById(id);
  }

  public @NonNull Context getContext() {
    return itemView.getContext();
  }

  public abstract void bind(@NonNull Model model);

  public void setPayload(@NonNull List<Object> payload) {
    this.payload.clear();
    this.payload.addAll(payload);
  }

  public static final class SimpleViewHolder<Model extends MappingModel<Model>> extends MappingViewHolder<Model> {
    public SimpleViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override
    public void bind(@NonNull Model model) { }
  }
}
