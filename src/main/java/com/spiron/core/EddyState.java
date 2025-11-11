package com.spiron.core;

import java.io.Serializable;

public record EddyState(String id, double[] vector, double energy)
  implements Serializable {
  private static final long serialVersionUID = 1L;
}
