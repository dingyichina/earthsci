/*******************************************************************************
 * Copyright 2012 Geoscience Australia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package au.gov.ga.earthsci.core.retrieve.retriever;

import au.gov.ga.earthsci.core.retrieve.IRetrieverMonitor;

/**
 * Exception thrown by the {@link MonitorInputStream} when the
 * {@link IRetrieverMonitor} indicates that the retrieval should be paused or
 * canceled.
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public class MonitorCancelledOrPausedException extends RuntimeException
{
}
