import React, { useEffect, useState } from 'react';
import { View, StyleSheet } from 'react-native';
import { feedback, InternalFeedbackState } from '../../services/feedbackService';
import { AppDialogModal } from './AppDialogModal';
import { AppToastOverlay } from './AppToastOverlay';

export const FeedbackContainer: React.FC = () => {
  const [state, setState] = useState<InternalFeedbackState>({
    dialog: null,
    toast: null,
  });

  useEffect(() => {
    return feedback.subscribe((newState) => {
      setState(newState);
    });
  }, []);

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <AppToastOverlay options={state.toast} />
      <AppDialogModal options={state.dialog} />
    </View>
  );
};
