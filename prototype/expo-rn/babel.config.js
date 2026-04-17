module.exports = function (api) {
  api.cache(true);

  return {
    presets: ['babel-preset-expo'],
    plugins: [
      'expo-router/babel',
      '@emotion/babel-plugin',
      'react-native-reanimated/plugin',
    ],
  };
};
