package dulinglai.android.ate.iccparser;

import com.google.protobuf.TextFormat;
import dulinglai.android.ate.iccparser.Ic3Data.Application;
import dulinglai.android.ate.iccparser.Ic3Data.Application.Component.ExitPoint;
import dulinglai.android.ate.iccparser.Ic3Data.Application.Component.Instruction;
import dulinglai.android.ate.iccparser.Ic3Data.Attribute;
import org.pmw.tinylog.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Ic3ResultLoader {

	public static App load(String resultConfigPath) {
		Ic3Data.Application application;
		try {
			FileInputStream inputStream = new FileInputStream(resultConfigPath);
			if (resultConfigPath.endsWith(".dat")) {
				application = Application.parseFrom(inputStream);
			} else {
				Application.Builder builder = Application.newBuilder();
				InputStreamReader reader = new InputStreamReader(inputStream);
				TextFormat.merge(reader, builder);
				application = builder.build();
			}
		} catch (IOException exception) {
			Logger.error("Problem opening or reading from file " + resultConfigPath, exception);
			return null;
		}

		Set<LoggingPoint> loggingPoints = new HashSet<>();

		App result = new App("IC3", application.getName());
		result.setComponentList(application.getComponentsList());

		for (Application.Component component : application.getComponentsList()) {
			for (ExitPoint exitPoint : component.getExitPointsList()) {
				LoggingPoint loggingPoint = new LoggingPoint(result);
				Instruction instruction = exitPoint.getInstruction();
				loggingPoint.setCallerMethodSignature(instruction.getMethod());
				loggingPoint.setStmtSequence(instruction.getId());
				String stmt = instruction.getStatement();
				int startPos = stmt.indexOf("<");
				int endPos = stmt.lastIndexOf(">");
				loggingPoint.setCalleeMethodSignature(stmt.substring(startPos, endPos + 1));
				Set<Intent> intents = new HashSet<Intent>();
				loggingPoint.setIntents(intents);

				for (ExitPoint.Intent intent : exitPoint.getIntentsList()) {
					Intent destinationIntent = new Intent(result, loggingPoint);

                    destinationIntent.setSourceClass(component.getName());

					String componentPackage = null;
					String componentClass = null;
					Set<String> categories = new HashSet<String>();
					Set<String> extras = new HashSet<String>();
					Set<Integer> flags = new HashSet<Integer>();

					for (Attribute attribute : intent.getAttributesList()) {
						switch (attribute.getKind()) {
						case PACKAGE:
							componentPackage = attribute.getValue(0);
							destinationIntent.setComponent(componentPackage);
							break;
						case CLASS:
							componentClass = attribute.getValue(0).replace('/', '.');
							destinationIntent.setComponentClass(componentClass.replace('/', '.'));
							// destinationIntent.set
							break;
						case ACTION:
							destinationIntent.setAction(attribute.getValue(0));
							break;
						case CATEGORY:
							categories.addAll(attribute.getValueList());
							break;
						case EXTRA:
							extras.addAll(attribute.getValueList());
							break;
						case SCHEME:
							destinationIntent.setDataScheme(attribute.getValue(0));
							break;
						case HOST:
							destinationIntent.setDataHost(attribute.getValue(0));
							break;
						case PORT:
							destinationIntent.setDataPort(attribute.getIntValue(0));
							break;
						case PATH:
							destinationIntent.setDataPath(attribute.getValue(0));
							break;
						case URI:
							destinationIntent.setData(attribute.getValue(0));
							break;
						case FLAG:
							flags.addAll(attribute.getIntValueList());
							break;
						case AUTHORITY:
							destinationIntent.setAuthority(attribute.getValue(0));
							break;
						case PRIORITY:
							break;
						case QUERY:
							break;
						case SSP:
							break;
						case TYPE:
							destinationIntent.setType(attribute.getValue(0));
							break;
						default:
							break;
						}
					}

					if (categories.size() != 0) {
						destinationIntent.setCategories(categories);
					}
					if (extras.size() != 0) {
						Map<String, String> extrasMap = new HashMap<String, String>();
						for (String extra : extras) {
							extrasMap.put(extra, "(.*)");
						}
						destinationIntent.setExtras(extrasMap);
					}
					if (flags.size() != 0) {
						int flagsInteger = 0;

						for (int flag : flags) {
							flagsInteger |= flag;
						}

						destinationIntent.setFlags(flagsInteger);
					}

					if (componentPackage != null && componentClass != null) {
						destinationIntent.setComponent(componentPackage + "/" + componentClass);
					}
					intents.add(destinationIntent);
				}
				loggingPoints.add(loggingPoint);
			}
		}

		result.setAnalysisTime((int) (application.getAnalysisEnd() - application.getAnalysisStart()));
		result.setLoggingPoints(loggingPoints);

		return result;
	}
}
