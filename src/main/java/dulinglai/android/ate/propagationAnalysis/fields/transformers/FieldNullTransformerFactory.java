/*
 * Copyright (C) 2015 The Pennsylvania State University and the University of Wisconsin
 * Systems and Internet Infrastructure Security Laboratory
 *
 * Author: Damien Octeau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dulinglai.android.ate.propagationAnalysis.fields.transformers;

import soot.Value;
import soot.jimple.Stmt;

/**
 * A factory for {@link NullFieldTransformer}, which always returns the same singleton.
 */
public class FieldNullTransformerFactory extends FieldTransformerFactory {
  @Override
  public FieldTransformer makeFieldTransformer(Object value) {
    return NullFieldTransformer.v();
  }

  @Override
  public FieldTransformer makeFieldTransformer(Value symbol, Stmt stmt, String op) {
    return NullFieldTransformer.v();
  }
}
