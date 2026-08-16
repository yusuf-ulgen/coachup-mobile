import { formatLocalDate } from '../utils/dateUtils';

export const HomeTabState = {
  selectedTabName: 'HOME',
  selectedDate: formatLocalDate(new Date()),
  hasUserSelectedTab: false,
  hasAppliedDefaultTab: false,
};
