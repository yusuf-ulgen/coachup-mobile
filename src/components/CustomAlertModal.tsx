import React from 'react';
import { feedback, FeedbackDialogOptions, FeedbackVariant, FeedbackButton } from '../services/feedbackService';
import { FeedbackContainer } from './feedback/FeedbackContainer';

export type AlertType = FeedbackVariant;
export type AlertButton = FeedbackButton;

export interface AlertOptions {
  title: string;
  message?: string;
  type?: AlertType;
  buttons?: AlertButton[];
}

export const CustomAlert = {
  show(options: AlertOptions) {
    feedback.showDialog({
      title: options.title,
      message: options.message,
      variant: options.type,
      buttons: options.buttons,
    });
  },
  hide() {
    feedback.hideDialog();
  },
  subscribe(listener: any) {
    return feedback.subscribe((state) => {
      if (state.dialog) {
        listener({
          title: state.dialog.title,
          message: state.dialog.message,
          type: state.dialog.variant,
          buttons: state.dialog.buttons,
        });
      } else {
        listener(null);
      }
    });
  },
};

export const CustomAlertContainer: React.FC = () => {
  return <FeedbackContainer />;
};
