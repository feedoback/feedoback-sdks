/**
 * The smallest app that exercises the SDK the way a customer would: a
 * Settings-style list with rows that open the sheet, the floating launcher, an
 * identity, a screen name, some context, and two kinds of thing that must not
 * appear in a screenshot.
 *
 * Reads its project from feedoback.config.json, which the verification script
 * writes, so the same bundle can be pointed at a dev server.
 */
import React from 'react';
import {
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  useColorScheme,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import {
  FeedbackProvider,
  FeedobackRedact,
  useFeedback,
  type FeedobackCategory,
} from 'feedoback-react-native';

import project from './feedoback.config.json';

// The emulator reaches the host machine at 10.0.2.2; the simulator shares
// localhost with it.
const HOST = Platform.select({
  android: 'http://10.0.2.2:3000',
  default: 'http://localhost:3000',
});

const ROWS: { label: string; category: FeedobackCategory }[] = [
  { label: 'Send feedback', category: 'feedback' },
  { label: 'Report a problem', category: 'bug' },
  { label: 'Suggest an idea', category: 'idea' },
];

function Settings() {
  const feedback = useFeedback();
  const dark = useColorScheme() === 'dark';
  const theme = dark ? styles.dark : styles.light;

  React.useEffect(() => {
    feedback.setScreen('settings', 'Settings');
  }, [feedback]);

  return (
    <SafeAreaView style={[styles.screen, theme]}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={[styles.title, theme]}>Settings</Text>

        <Text style={[styles.legend, theme]}>Account</Text>
        <View style={styles.row}>
          <Text style={[styles.rowLabel, theme]}>Password</Text>
          {/* A real secure field, which the SDK finds without being told. */}
          <TextInput
            style={[styles.input, theme]}
            secureTextEntry
            editable={false}
            value="hunter2hunter2"
          />
        </View>

        <View style={styles.row}>
          <Text style={[styles.rowLabel, theme]}>Card</Text>
          {/* Nothing marks this one out to the SDK, so the app says so. */}
          <FeedobackRedact style={styles.redact}>
            <Text style={[styles.card, theme]}>4242 4242 4242 4242</Text>
          </FeedobackRedact>
        </View>

        <Text style={[styles.legend, theme]}>Feedback</Text>
        {ROWS.map(({ label, category }) => (
          <Pressable
            key={category}
            accessibilityRole="button"
            accessibilityLabel={label}
            style={styles.action}
            onPress={() => feedback.present(category)}>
            <Text style={[styles.actionLabel, theme]}>{label}</Text>
          </Pressable>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

export default function App() {
  const visitor = project.visitorId
    ? {
        id: project.visitorId,
        email: project.visitorEmail,
        name: 'Ada Lovelace',
        userHash: project.userHash,
      }
    : undefined;

  return (
    <SafeAreaProvider>
      <FeedbackProvider
        projectKey={project.projectKey}
        host={HOST}
        categories={['feedback', 'bug', 'idea']}
        launcher={{ enabled: true, style: 'labelled', corner: 'bottom-end' }}
        logLevel="debug"
        visitor={visitor}
        context={{ plan: 'pro', seats: 12, trial: false }}>
        <Settings />
      </FeedbackProvider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  content: { padding: 20, paddingTop: 40 },
  title: { fontSize: 28, fontWeight: '700' },
  legend: { fontSize: 13, opacity: 0.6, marginTop: 28, marginBottom: 8 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
  },
  rowLabel: { fontSize: 17 },
  input: { fontSize: 17, width: 170, textAlign: 'right' },
  redact: { paddingHorizontal: 4 },
  card: { fontSize: 17, fontVariant: ['tabular-nums'] },
  action: { paddingVertical: 16 },
  actionLabel: { fontSize: 17 },
  light: { backgroundColor: '#ffffff', color: '#111111' },
  dark: { backgroundColor: '#0b0b0c', color: '#f4f4f5' },
});
