/*
 * *****************************************************************************
 * Copyright (C) 2014-2024 Dennis Sheirer
 * Copyright (C) 2026 Benjamin Vernoux
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */
package io.github.dsheirer.source.tuner.hydrasdr;

import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.source.SourceException;
import io.github.dsheirer.source.tuner.manager.DiscoveredTuner;
import io.github.dsheirer.source.tuner.manager.TunerManager;
import io.github.dsheirer.source.tuner.manager.TunerStatus;
import io.github.dsheirer.source.tuner.ui.TunerEditor;
import java.util.List;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.SpinnerNumberModel;

/**
 * HydraSDR tuner editor using native libhydrasdr API.
 *
 * Dynamically builds gain controls based on device capability flags.
 */
public class HydraSdrTunerEditor extends TunerEditor<HydraSdrTuner, HydraSdrTunerConfiguration>
{
	private static final long serialVersionUID = 1L;
	private static final Logger mLog = LoggerFactory.getLogger(HydraSdrTunerEditor.class);

	private static final String[] GAIN_MODES = {"Linearity", "Sensitivity", "Custom"};

	private JButton mTunerInfoButton;
	private JComboBox<HydraSdrSampleRate> mSampleRateCombo;
	private JComboBox<String> mGainModeCombo;
	private JSlider mMasterGainSlider;
	private JLabel mMasterGainLabel;
	private JLabel mMasterGainValueLabel;
	private JSlider mVgaGainSlider;
	private JLabel mVgaGainLabel;
	private JLabel mVgaGainValueLabel;
	private JSlider mLnaGainSlider;
	private JLabel mLnaGainValueLabel;
	private JSlider mRfGainSlider;
	private JLabel mRfGainValueLabel;
	private JSlider mMixerGainSlider;
	private JLabel mMixerGainValueLabel;
	private JSlider mFilterGainSlider;
	private JLabel mFilterGainValueLabel;
	private JCheckBox mLnaAgcCheckBox;
	private JCheckBox mRfAgcCheckBox;
	private JCheckBox mMixerAgcCheckBox;
	private JCheckBox mFilterAgcCheckBox;
	private JCheckBox mBiasTCheckBox;
	private JComboBox<Integer> mRfPortCombo;

	public HydraSdrTunerEditor(UserPreferences userPreferences, TunerManager tunerManager,
		DiscoveredTuner discoveredTuner)
	{
		super(userPreferences, tunerManager, discoveredTuner);
		init();
		tunerStatusUpdated();
	}

	@Override
	public long getMinimumTunableFrequency()
	{
		if(hasTuner())
		{
			HydraSdrDeviceInfo info = getTuner().getController().getDeviceInfo();
			if(info != null && info.getMinFrequency() > 0)
			{
				return info.getMinFrequency();
			}
		}
		return HydraSdrTunerController.FALLBACK_MIN_FREQUENCY_HZ;
	}

	@Override
	public long getMaximumTunableFrequency()
	{
		if(hasTuner())
		{
			HydraSdrDeviceInfo info = getTuner().getController().getDeviceInfo();
			if(info != null && info.getMaxFrequency() > 0)
			{
				return info.getMaxFrequency();
			}
		}
		return HydraSdrTunerController.FALLBACK_MAX_FREQUENCY_HZ;
	}

	@Override
	protected void tunerStatusUpdated()
	{
		setLoading(true);

		if(hasTuner())
		{
			getTunerIdLabel().setText(getTuner().getPreferredName());
		}
		else
		{
			getTunerIdLabel().setText(getDiscoveredTuner().getId());
		}

		String status = getDiscoveredTuner().getTunerStatus().toString();
		if(getDiscoveredTuner().hasErrorMessage())
		{
			status += " - " + getDiscoveredTuner().getErrorMessage();
		}
		getTunerStatusLabel().setText(status);
		getButtonPanel().updateControls();
		getFrequencyPanel().updateControls();
		getSampleRateCombo().setEnabled(hasTuner() && !getTuner().getTunerController().isLockedSampleRate());
		getTunerInfoButton().setEnabled(hasTuner());
		HydraSdrDeviceInfo deviceInfo = hasTuner() ? getTuner().getController().getDeviceInfo() : null;
		boolean supportsBiasT = deviceInfo != null && deviceInfo.hasCapability(HydraSdrNative.CAP_BIAS_TEE);
		getBiasTCheckBox().setEnabled(supportsBiasT);
		updateRfPortControls(deviceInfo);

		if(hasConfiguration())
		{
			getBiasTCheckBox().setSelected(supportsBiasT && getConfiguration().isBiasT());
		}

		updateGainControls();

		if(hasTuner())
		{
			List<HydraSdrSampleRate> rates = getTuner().getController().getSampleRates();
			getSampleRateCombo().setModel(new DefaultComboBoxModel<>(
				rates.toArray(new HydraSdrSampleRate[0])));

			if(hasConfiguration())
			{
				HydraSdrSampleRate sampleRate = getTuner().getController()
					.getSampleRate(getConfiguration().getSampleRate());
				getSampleRateCombo().setSelectedItem(sampleRate);
			}
		}
		else
		{
			getSampleRateCombo().setModel(new DefaultComboBoxModel<>());
		}

		setLoading(false);
	}

	private void init()
	{
		setLayout(new MigLayout("fill,wrap 3", "[right][grow,fill][fill]",
			"[][][][][][][][][][][][][][][][grow]"));

		add(new JLabel("Tuner:"));
		add(getTunerIdLabel());
		add(getTunerInfoButton());

		add(new JLabel("Status:"));
		add(getTunerStatusLabel(), "wrap");

		add(getButtonPanel(), "span,align left");

		add(new JSeparator(), "span,growx,push");

		add(new JLabel("Frequency (MHz):"));
		add(getFrequencyPanel(), "wrap");

		add(new JLabel("Sample Rate:"));
		add(getSampleRateCombo(), "wrap");

		add(new JLabel("RF Port:"));
		add(getRfPortCombo(), "wrap");

		add(new JLabel());
		add(getBiasTCheckBox(), "wrap");

		add(new JSeparator(), "span,growx,push");
		add(new JLabel("Gain Control"), "wrap");

		add(new JLabel("Mode:"));
		add(getGainModeCombo(), "wrap");

		add(getMasterGainLabel());
		add(getMasterGainSlider());
		add(getMasterGainValueLabel());

		add(getVgaGainLabel());
		add(getVgaGainSlider());
		add(getVgaGainValueLabel());

		add(getFilterAgcCheckBox());
		add(getFilterGainSlider());
		add(getFilterGainValueLabel());

		add(getMixerAgcCheckBox());
		add(getMixerGainSlider());
		add(getMixerGainValueLabel());

		add(getRfAgcCheckBox());
		add(getRfGainSlider());
		add(getRfGainValueLabel());

		add(getLnaAgcCheckBox());
		add(getLnaGainSlider());
		add(getLnaGainValueLabel());
	}

	private JCheckBox getLnaAgcCheckBox()
	{
		if(mLnaAgcCheckBox == null)
		{
			mLnaAgcCheckBox = new JCheckBox("AGC LNA:");
			mLnaAgcCheckBox.setEnabled(false);
			mLnaAgcCheckBox.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					try
					{
						HydraSdrTunerController controller = getTuner().getController();
						controller.setGain(HydraSdrNative.GAIN_TYPE_LNA_AGC,
							mLnaAgcCheckBox.isSelected() ? 1 : 0);
						HydraSdrDeviceInfo deviceInfo = controller.getDeviceInfo();
						getLnaGainSlider().setEnabled(!mLnaAgcCheckBox.isSelected() && deviceInfo != null &&
							deviceInfo.hasCapability(HydraSdrNative.CAP_LNA_GAIN));
						save();
					}
					catch(Exception e1)
					{
						mLog.error("Error setting LNA AGC", e1);
					}
				}
			});
		}
		return mLnaAgcCheckBox;
	}

	private JCheckBox getRfAgcCheckBox()
	{
		if(mRfAgcCheckBox == null)
		{
			mRfAgcCheckBox = new JCheckBox("AGC RF:");
			mRfAgcCheckBox.setEnabled(false);
			mRfAgcCheckBox.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					try
					{
						HydraSdrTunerController controller = getTuner().getController();
						controller.setGain(HydraSdrNative.GAIN_TYPE_RF_AGC,
							mRfAgcCheckBox.isSelected() ? 1 : 0);
						HydraSdrDeviceInfo deviceInfo = controller.getDeviceInfo();
						getRfGainSlider().setEnabled(!mRfAgcCheckBox.isSelected() && deviceInfo != null &&
							deviceInfo.hasCapability(HydraSdrNative.CAP_RF_GAIN));
						save();
					}
					catch(Exception e1)
					{
						mLog.error("Error setting RF AGC", e1);
					}
				}
			});
		}
		return mRfAgcCheckBox;
	}

	private JCheckBox getMixerAgcCheckBox()
	{
		if(mMixerAgcCheckBox == null)
		{
			mMixerAgcCheckBox = new JCheckBox("AGC Mixer:");
			mMixerAgcCheckBox.setEnabled(false);
			mMixerAgcCheckBox.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					try
					{
						HydraSdrTunerController controller = getTuner().getController();
						controller.setGain(HydraSdrNative.GAIN_TYPE_MIXER_AGC,
							mMixerAgcCheckBox.isSelected() ? 1 : 0);
						HydraSdrDeviceInfo deviceInfo = controller.getDeviceInfo();
						getMixerGainSlider().setEnabled(!mMixerAgcCheckBox.isSelected() && deviceInfo != null &&
							deviceInfo.hasCapability(HydraSdrNative.CAP_MIXER_GAIN));
						save();
					}
					catch(Exception e1)
					{
						mLog.error("Error setting Mixer AGC", e1);
					}
				}
			});
		}
		return mMixerAgcCheckBox;
	}

	private JCheckBox getFilterAgcCheckBox()
	{
		if(mFilterAgcCheckBox == null)
		{
			mFilterAgcCheckBox = new JCheckBox("AGC Filter:");
			mFilterAgcCheckBox.setEnabled(false);
			mFilterAgcCheckBox.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					try
					{
						HydraSdrTunerController controller = getTuner().getController();
						controller.setGain(HydraSdrNative.GAIN_TYPE_FILTER_AGC,
							mFilterAgcCheckBox.isSelected() ? 1 : 0);
						HydraSdrDeviceInfo deviceInfo = controller.getDeviceInfo();
						getFilterGainSlider().setEnabled(!mFilterAgcCheckBox.isSelected() && deviceInfo != null &&
							deviceInfo.hasCapability(HydraSdrNative.CAP_FILTER_GAIN));
						save();
					}
					catch(Exception e1)
					{
						mLog.error("Error setting filter AGC", e1);
					}
				}
			});
		}
		return mFilterAgcCheckBox;
	}

	private JComboBox<Integer> getRfPortCombo()
	{
		if(mRfPortCombo == null)
		{
			mRfPortCombo = new JComboBox<>();
			mRfPortCombo.setEnabled(false);
			mRfPortCombo.addActionListener(e ->
			{
				if(hasTuner() && !isLoading() && mRfPortCombo.getSelectedItem() instanceof Integer rfPort)
				{
					try
					{
						getTuner().getController().setRfPort(rfPort);
						save();
					}
					catch(Exception e1)
					{
						mLog.error("Error selecting RF port " + rfPort, e1);
						JOptionPane.showMessageDialog(mRfPortCombo,
							"Couldn't select RF port " + rfPort);
					}
				}
			});
		}
		return mRfPortCombo;
	}

	private void updateRfPortControls(HydraSdrDeviceInfo deviceInfo)
	{
		boolean supported = deviceInfo != null &&
			deviceInfo.hasCapability(HydraSdrNative.CAP_RF_PORT_SELECT) && deviceInfo.getRfPortCount() > 0;
		Integer[] ports = new Integer[supported ? deviceInfo.getRfPortCount() : 0];
		for(int x = 0; x < ports.length; x++)
		{
			ports[x] = x;
		}
		getRfPortCombo().setModel(new DefaultComboBoxModel<>(ports));
		getRfPortCombo().setEnabled(supported);
		if(supported && hasConfiguration())
		{
			getRfPortCombo().setSelectedItem(HydraSdrTunerController.normalizeRfPort(
				getConfiguration().getRfPort(), deviceInfo.getRfPortCount()));
		}
	}

	private JCheckBox getBiasTCheckBox()
	{
		if(mBiasTCheckBox == null)
		{
			mBiasTCheckBox = new JCheckBox("Bias-T");
			mBiasTCheckBox.setEnabled(false);
			mBiasTCheckBox.setToolTipText("Enable Bias-T power output for active antennas");
			mBiasTCheckBox.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setBiasT(mBiasTCheckBox.isSelected());
						save();
					}
					catch(Exception e1)
					{
						mLog.error("Error setting Bias-T", e1);
						JOptionPane.showMessageDialog(mBiasTCheckBox,
							"Couldn't set Bias-T: " + e1.getMessage());
					}
				}
			});
		}
		return mBiasTCheckBox;
	}

	private JLabel getLnaGainValueLabel()
	{
		if(mLnaGainValueLabel == null)
		{
			mLnaGainValueLabel = new JLabel("0");
			mLnaGainValueLabel.setEnabled(false);
		}
		return mLnaGainValueLabel;
	}

	private JSlider getLnaGainSlider()
	{
		if(mLnaGainSlider == null)
		{
			mLnaGainSlider = new JSlider(JSlider.HORIZONTAL, 0, 14, 0);
			mLnaGainSlider.setEnabled(false);
			mLnaGainSlider.setMajorTickSpacing(1);
			mLnaGainSlider.setPaintTicks(true);
			mLnaGainSlider.addChangeListener(event ->
			{
				int gain = mLnaGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_LNA, gain);
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set LNA gain to:" + gain, e);
						JOptionPane.showMessageDialog(mLnaGainSlider,
							"Couldn't set LNA gain value to " + gain);
					}
				}
				getLnaGainValueLabel().setText(String.valueOf(gain));
			});
		}
		return mLnaGainSlider;
	}

	private JLabel getRfGainValueLabel()
	{
		if(mRfGainValueLabel == null)
		{
			mRfGainValueLabel = new JLabel("0");
			mRfGainValueLabel.setEnabled(false);
		}
		return mRfGainValueLabel;
	}

	private JSlider getRfGainSlider()
	{
		if(mRfGainSlider == null)
		{
			mRfGainSlider = new JSlider(JSlider.HORIZONTAL, 0, 15, 0);
			mRfGainSlider.setEnabled(false);
			mRfGainSlider.setMajorTickSpacing(1);
			mRfGainSlider.setPaintTicks(true);
			mRfGainSlider.addChangeListener(event ->
			{
				int gain = mRfGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_RF, gain);
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set RF gain to:" + gain, e);
						JOptionPane.showMessageDialog(mRfGainSlider,
							"Couldn't set RF gain value to " + gain);
					}
				}
				getRfGainValueLabel().setText(String.valueOf(gain));
			});
		}
		return mRfGainSlider;
	}

	private JLabel getMixerGainValueLabel()
	{
		if(mMixerGainValueLabel == null)
		{
			mMixerGainValueLabel = new JLabel("0");
			mMixerGainValueLabel.setEnabled(false);
		}
		return mMixerGainValueLabel;
	}

	private JSlider getMixerGainSlider()
	{
		if(mMixerGainSlider == null)
		{
			mMixerGainSlider = new JSlider(JSlider.HORIZONTAL, 0, 15, 0);
			mMixerGainSlider.setEnabled(false);
			mMixerGainSlider.setMajorTickSpacing(1);
			mMixerGainSlider.setPaintTicks(true);
			mMixerGainSlider.addChangeListener(event ->
			{
				int gain = mMixerGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_MIXER, gain);
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set Mixer gain to:" + gain, e);
						JOptionPane.showMessageDialog(mMixerGainSlider,
							"Couldn't set Mixer gain value to " + gain);
					}
				}
				getMixerGainValueLabel().setText(String.valueOf(gain));
			});
		}
		return mMixerGainSlider;
	}

	private JLabel getFilterGainValueLabel()
	{
		if(mFilterGainValueLabel == null)
		{
			mFilterGainValueLabel = new JLabel("0");
			mFilterGainValueLabel.setEnabled(false);
		}
		return mFilterGainValueLabel;
	}

	private JSlider getFilterGainSlider()
	{
		if(mFilterGainSlider == null)
		{
			mFilterGainSlider = new JSlider(JSlider.HORIZONTAL, 0, 15, 0);
			mFilterGainSlider.setEnabled(false);
			mFilterGainSlider.setMajorTickSpacing(1);
			mFilterGainSlider.setPaintTicks(true);
			mFilterGainSlider.addChangeListener(event ->
			{
				int gain = mFilterGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_FILTER, gain);
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set filter gain to:" + gain, e);
						JOptionPane.showMessageDialog(mFilterGainSlider,
							"Couldn't set filter gain value to " + gain);
					}
				}
				getFilterGainValueLabel().setText(String.valueOf(gain));
			});
		}
		return mFilterGainSlider;
	}

	private JLabel getVgaGainLabel()
	{
		if(mVgaGainLabel == null)
		{
			mVgaGainLabel = new JLabel("IF:");
		}
		return mVgaGainLabel;
	}

	private JLabel getVgaGainValueLabel()
	{
		if(mVgaGainValueLabel == null)
		{
			mVgaGainValueLabel = new JLabel("0");
			mVgaGainValueLabel.setEnabled(false);
		}
		return mVgaGainValueLabel;
	}

	private JSlider getVgaGainSlider()
	{
		if(mVgaGainSlider == null)
		{
			mVgaGainSlider = new JSlider(JSlider.HORIZONTAL, 0, 15, 0);
			mVgaGainSlider.setEnabled(false);
			mVgaGainSlider.setMajorTickSpacing(1);
			mVgaGainSlider.setPaintTicks(true);
			mVgaGainSlider.addChangeListener(event ->
			{
				int gain = mVgaGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						getTuner().getController().setGain(HydraSdrNative.GAIN_TYPE_VGA, gain);
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set VGA gain to:" + gain, e);
						JOptionPane.showMessageDialog(mVgaGainSlider,
							"Couldn't set IF gain value to " + gain);
					}
				}
				getVgaGainValueLabel().setText(String.valueOf(gain));
			});
		}
		return mVgaGainSlider;
	}

	private JLabel getMasterGainLabel()
	{
		if(mMasterGainLabel == null)
		{
			mMasterGainLabel = new JLabel("Master:");
		}
		return mMasterGainLabel;
	}

	private JLabel getMasterGainValueLabel()
	{
		if(mMasterGainValueLabel == null)
		{
			mMasterGainValueLabel = new JLabel("0");
			mMasterGainValueLabel.setEnabled(false);
		}
		return mMasterGainValueLabel;
	}

	private JSlider getMasterGainSlider()
	{
		if(mMasterGainSlider == null)
		{
			mMasterGainSlider = new JSlider(JSlider.HORIZONTAL, 1, 22, 14);
			mMasterGainSlider.setEnabled(false);
			mMasterGainSlider.setMajorTickSpacing(1);
			mMasterGainSlider.setPaintTicks(true);
			mMasterGainSlider.addChangeListener(event ->
			{
				int value = mMasterGainSlider.getValue();
				if(hasTuner() && !isLoading())
				{
					try
					{
						int mode = getGainModeCombo().getSelectedIndex();
						if(mode == 0)
						{
							getTuner().getController().setGain(
								HydraSdrNative.GAIN_TYPE_LINEARITY, value);
						}
						else if(mode == 1)
						{
							getTuner().getController().setGain(
								HydraSdrNative.GAIN_TYPE_SENSITIVITY, value);
						}
						save();
					}
					catch(Exception e)
					{
						mLog.error("Couldn't set master gain to:" + value, e);
						JOptionPane.showMessageDialog(mMasterGainSlider,
							"Couldn't set gain value to " + value);
					}
				}
				getMasterGainValueLabel().setText(String.valueOf(value));
			});
		}
		return mMasterGainSlider;
	}

	private JComboBox<String> getGainModeCombo()
	{
		if(mGainModeCombo == null)
		{
			mGainModeCombo = new JComboBox<>(GAIN_MODES);
			mGainModeCombo.setEnabled(false);
			mGainModeCombo.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					int mode = mGainModeCombo.getSelectedIndex();
					mLog.info("Gain mode changed to: " + GAIN_MODES[mode] + " (" + mode + ")");

					/* Apply the mode change to the device */
					try
					{
						HydraSdrTunerController controller = getTuner().getController();
						if(!controller.supportsGainMode(mode))
						{
							throw new SourceException("Gain mode is not supported by this device");
						}

						int presetValue = mode == HydraSdrTunerController.GAIN_MODE_CUSTOM ? -1 :
							preparePresetGain(controller, mode);
						if(mode == 0)
						{
							/* Linearity: disable AGCs, apply preset */
							HydraSdrDeviceInfo deviceInfo = controller.getDeviceInfo();
							if(deviceInfo.hasCapability(HydraSdrNative.CAP_LNA_AGC))
							{
								controller.setGain(HydraSdrNative.GAIN_TYPE_LNA_AGC, 0);
							}
							if(deviceInfo.hasCapability(HydraSdrNative.CAP_RF_AGC))
							{
								controller.setGain(HydraSdrNative.GAIN_TYPE_RF_AGC, 0);
							}
							if(deviceInfo.hasCapability(HydraSdrNative.CAP_MIXER_AGC))
							{
								controller.setGain(HydraSdrNative.GAIN_TYPE_MIXER_AGC, 0);
							}
							if(deviceInfo.hasCapability(HydraSdrNative.CAP_FILTER_AGC))
							{
								controller.setGain(HydraSdrNative.GAIN_TYPE_FILTER_AGC, 0);
							}
							getTuner().getController().setGain(
								HydraSdrNative.GAIN_TYPE_LINEARITY, presetValue);
						}
						else if(mode == 1)
						{
							/* Sensitivity: disable AGCs, apply preset */
							HydraSdrDeviceInfo deviceInfo = controller.getDeviceInfo();
							if(deviceInfo.hasCapability(HydraSdrNative.CAP_LNA_AGC))
							{
								controller.setGain(HydraSdrNative.GAIN_TYPE_LNA_AGC, 0);
							}
							if(deviceInfo.hasCapability(HydraSdrNative.CAP_RF_AGC))
							{
								controller.setGain(HydraSdrNative.GAIN_TYPE_RF_AGC, 0);
							}
							if(deviceInfo.hasCapability(HydraSdrNative.CAP_MIXER_AGC))
							{
								controller.setGain(HydraSdrNative.GAIN_TYPE_MIXER_AGC, 0);
							}
							if(deviceInfo.hasCapability(HydraSdrNative.CAP_FILTER_AGC))
							{
								controller.setGain(HydraSdrNative.GAIN_TYPE_FILTER_AGC, 0);
							}
							getTuner().getController().setGain(
								HydraSdrNative.GAIN_TYPE_SENSITIVITY, presetValue);
						}
						else
						{
							controller.applyCustomGains(getConfiguration());
						}
						save();
					}
					catch(Exception ex)
					{
						mLog.error("Error applying gain mode", ex);
						setLoading(true);
						getGainModeCombo().setSelectedIndex(getConfiguration().getGainMode());
						setLoading(false);
					}

					setLoading(true);
					try
					{
						updateGainControls();
					}
					finally
					{
						setLoading(false);
					}
				}
			});
		}
		return mGainModeCombo;
	}

	private JComboBox<HydraSdrSampleRate> getSampleRateCombo()
	{
		if(mSampleRateCombo == null)
		{
			mSampleRateCombo = new JComboBox<>();
			mSampleRateCombo.setEnabled(false);
			mSampleRateCombo.addActionListener(e ->
			{
				if(hasTuner() && !isLoading())
				{
					HydraSdrSampleRate rate = (HydraSdrSampleRate)mSampleRateCombo.getSelectedItem();
					if(rate != null)
					{
						try
						{
							getTuner().getController().setSampleRate(rate);
							adjustForSampleRate(rate.getRate());
							save();
						}
						catch(Exception e1)
						{
							JOptionPane.showMessageDialog(HydraSdrTunerEditor.this,
								"Couldn't set sample rate to " + rate.getLabel());
							mLog.error("Error setting sample rate", e1);
						}
					}
				}
			});
		}
		return mSampleRateCombo;
	}

	private JButton getTunerInfoButton()
	{
		if(mTunerInfoButton == null)
		{
			mTunerInfoButton = new JButton("Info");
			mTunerInfoButton.setEnabled(false);
			mTunerInfoButton.addActionListener(e -> JOptionPane.showMessageDialog(
				HydraSdrTunerEditor.this, getTunerInfo(), "Tuner Info",
				JOptionPane.INFORMATION_MESSAGE));
		}
		return mTunerInfoButton;
	}

	private int preparePresetGain(HydraSdrTunerController controller, int mode)
	{
		int gainType = mode == HydraSdrTunerController.GAIN_MODE_LINEARITY ?
			HydraSdrNative.GAIN_TYPE_LINEARITY : HydraSdrNative.GAIN_TYPE_SENSITIVITY;
		int savedValue = mode == HydraSdrTunerController.GAIN_MODE_LINEARITY ?
			getConfiguration().getLinearityGain() : getConfiguration().getSensitivityGain();
		int fallback = mode == HydraSdrTunerController.GAIN_MODE_LINEARITY ?
			HydraSdrTunerController.LINEARITY_GAIN_DEFAULT : HydraSdrTunerController.SENSITIVITY_GAIN_DEFAULT;
		int[] gainInfo = controller.getGainInfo(gainType);
		int value = HydraSdrTunerController.normalizePresetGain(savedValue, fallback, gainInfo);

		setLoading(true);
		try
		{
			updateSliderRange(getMasterGainSlider(), gainInfo);
			getMasterGainSlider().setValue(value);
			getMasterGainValueLabel().setText(String.valueOf(value));
		}
		finally
		{
			setLoading(false);
		}
		return value;
	}

	/**
	 * Updates gain control enabled/disabled state based on gain mode.
	 * Reads the current mode from the combo box (user selection), not from saved config.
	 */
	private void updateGainControls()
	{
		if(hasTuner())
		{
			int mode = getGainModeCombo().getSelectedIndex();
			if(mode < 0)
			{
				mode = hasConfiguration() ? getConfiguration().getGainMode() : 0;
			}
			boolean isCustom = (mode == 2);
			HydraSdrDeviceInfo deviceInfo = getTuner().getController().getDeviceInfo();

			getGainModeCombo().setEnabled(true);

			/* Only set combo index from config during loading (not user interaction) */
			if(isLoading() && hasConfiguration())
			{
				getGainModeCombo().setSelectedIndex(getConfiguration().getGainMode());
				mode = getConfiguration().getGainMode();
				isCustom = (mode == 2);
			}

			getMasterGainLabel().setEnabled(!isCustom);
			getMasterGainSlider().setEnabled(!isCustom);
			getMasterGainValueLabel().setEnabled(!isCustom);
			boolean supportsVgaGain = deviceInfo != null && deviceInfo.hasCapability(HydraSdrNative.CAP_VGA_GAIN);
			boolean supportsLnaGain = deviceInfo != null && deviceInfo.hasCapability(HydraSdrNative.CAP_LNA_GAIN);
			boolean supportsRfGain = deviceInfo != null && deviceInfo.hasCapability(HydraSdrNative.CAP_RF_GAIN);
			boolean supportsMixerGain = deviceInfo != null && deviceInfo.hasCapability(HydraSdrNative.CAP_MIXER_GAIN);
			boolean supportsFilterGain = deviceInfo != null && deviceInfo.hasCapability(HydraSdrNative.CAP_FILTER_GAIN);
			boolean supportsLnaAgc = deviceInfo != null && deviceInfo.hasCapability(HydraSdrNative.CAP_LNA_AGC);
			boolean supportsRfAgc = deviceInfo != null && deviceInfo.hasCapability(HydraSdrNative.CAP_RF_AGC);
			boolean supportsMixerAgc = deviceInfo != null && deviceInfo.hasCapability(HydraSdrNative.CAP_MIXER_AGC);
			boolean supportsFilterAgc = deviceInfo != null && deviceInfo.hasCapability(HydraSdrNative.CAP_FILTER_AGC);
			getVgaGainLabel().setEnabled(isCustom && supportsVgaGain);
			getVgaGainSlider().setEnabled(isCustom && supportsVgaGain);
			getVgaGainValueLabel().setEnabled(isCustom && supportsVgaGain);
			getLnaAgcCheckBox().setEnabled(isCustom && supportsLnaAgc);
			getLnaGainSlider().setEnabled(isCustom && supportsLnaGain &&
				!(supportsLnaAgc && hasConfiguration() && getConfiguration().isLnaAgc()));
			getLnaGainValueLabel().setEnabled(isCustom && supportsLnaGain);
			getRfAgcCheckBox().setEnabled(isCustom && supportsRfAgc);
			getRfGainSlider().setEnabled(isCustom && supportsRfGain &&
				!(supportsRfAgc && hasConfiguration() && getConfiguration().isRfAgc()));
			getRfGainValueLabel().setEnabled(isCustom && supportsRfGain);
			getMixerAgcCheckBox().setEnabled(isCustom && supportsMixerAgc);
			getMixerGainSlider().setEnabled(isCustom && supportsMixerGain &&
				!(supportsMixerAgc && hasConfiguration() && getConfiguration().isMixerAgc()));
			getMixerGainValueLabel().setEnabled(isCustom && supportsMixerGain);
			getFilterAgcCheckBox().setEnabled(isCustom && supportsFilterAgc);
			getFilterGainSlider().setEnabled(isCustom && supportsFilterGain &&
				!(supportsFilterAgc && hasConfiguration() && getConfiguration().isFilterAgc()));
			getFilterGainValueLabel().setEnabled(isCustom && supportsFilterGain);

			/* Expand and step slider models before restoring saved values to avoid clamping. */
			updateGainRangesFromDevice(mode);

			if(hasConfiguration())
			{
				if(isCustom)
				{
					getVgaGainSlider().setValue(getConfiguration().getVgaGain());
					getLnaGainSlider().setValue(getConfiguration().getLnaGain());
					getRfGainSlider().setValue(getConfiguration().getRfGain());
					getMixerGainSlider().setValue(getConfiguration().getMixerGain());
					getFilterGainSlider().setValue(getConfiguration().getFilterGain());
					getLnaAgcCheckBox().setSelected(getConfiguration().isLnaAgc());
					getRfAgcCheckBox().setSelected(getConfiguration().isRfAgc());
					getMixerAgcCheckBox().setSelected(getConfiguration().isMixerAgc());
					getFilterAgcCheckBox().setSelected(getConfiguration().isFilterAgc());
				}
				else if(mode == 0)
				{
					int lin = getConfiguration().getLinearityGain();
					getMasterGainSlider().setValue(lin >= 0 ? lin : 14);
				}
				else
				{
					int sens = getConfiguration().getSensitivityGain();
					getMasterGainSlider().setValue(sens >= 0 ? sens : 10);
				}
			}
		}
		else
		{
			getGainModeCombo().setEnabled(false);
			getMasterGainLabel().setEnabled(false);
			getMasterGainSlider().setEnabled(false);
			getMasterGainSlider().setValue(1);
			getMasterGainValueLabel().setEnabled(false);
			getVgaGainLabel().setEnabled(false);
			getVgaGainSlider().setEnabled(false);
			getVgaGainSlider().setValue(0);
			getVgaGainValueLabel().setEnabled(false);
			getLnaAgcCheckBox().setEnabled(false);
			getLnaAgcCheckBox().setSelected(false);
			getLnaGainSlider().setEnabled(false);
			getLnaGainSlider().setValue(0);
			getLnaGainValueLabel().setEnabled(false);
			getRfAgcCheckBox().setEnabled(false);
			getRfAgcCheckBox().setSelected(false);
			getRfGainSlider().setEnabled(false);
			getRfGainSlider().setValue(0);
			getRfGainValueLabel().setEnabled(false);
			getMixerAgcCheckBox().setEnabled(false);
			getMixerAgcCheckBox().setSelected(false);
			getMixerGainSlider().setEnabled(false);
			getMixerGainSlider().setValue(0);
			getMixerGainValueLabel().setEnabled(false);
			getFilterAgcCheckBox().setEnabled(false);
			getFilterAgcCheckBox().setSelected(false);
			getFilterGainSlider().setEnabled(false);
			getFilterGainSlider().setValue(0);
			getFilterGainValueLabel().setEnabled(false);
		}
	}

	/**
	 * Queries device for actual gain ranges and updates slider min/max.
	 */
	private void updateGainRangesFromDevice(int mode)
	{
		if(!hasTuner())
		{
			return;
		}

		HydraSdrTunerController ctrl = getTuner().getController();

		updateSliderRange(getLnaGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_LNA));
		updateSliderRange(getRfGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_RF));
		updateSliderRange(getMixerGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_MIXER));
		updateSliderRange(getFilterGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_FILTER));
		updateSliderRange(getVgaGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_VGA));
		if(mode == HydraSdrTunerController.GAIN_MODE_LINEARITY)
		{
			updateSliderRange(getMasterGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_LINEARITY));
		}
		else if(mode == HydraSdrTunerController.GAIN_MODE_SENSITIVITY)
		{
			updateSliderRange(getMasterGainSlider(), ctrl.getGainInfo(HydraSdrNative.GAIN_TYPE_SENSITIVITY));
		}
	}

	private void updateSliderRange(JSlider slider, int[] gainInfo)
	{
		if(gainInfo != null && gainInfo.length > HydraSdrNative.GAIN_INFO_STEP)
		{
			slider.setMinimum(gainInfo[HydraSdrNative.GAIN_INFO_MIN]);
			slider.setMaximum(gainInfo[HydraSdrNative.GAIN_INFO_MAX]);
			int step = Math.max(1, gainInfo[HydraSdrNative.GAIN_INFO_STEP]);
			slider.setMinorTickSpacing(step);
			slider.setSnapToTicks(step > 1);
		}
	}

	static void savePresetGain(HydraSdrTunerConfiguration configuration, int mode, int gain)
	{
		if(mode == HydraSdrTunerController.GAIN_MODE_LINEARITY)
		{
			configuration.setLinearityGain(gain);
		}
		else if(mode == HydraSdrTunerController.GAIN_MODE_SENSITIVITY)
		{
			configuration.setSensitivityGain(gain);
		}
	}

	@Override
	public void save()
	{
		if(hasConfiguration() && !isLoading())
		{
			getConfiguration().setFrequency(getFrequencyControl().getFrequency());
			getConfiguration().setMinimumFrequency(getMinimumFrequencyTextField().getFrequency());
			getConfiguration().setMaximumFrequency(getMaximumFrequencyTextField().getFrequency());
			double value = ((SpinnerNumberModel)getFrequencyCorrectionSpinner()
				.getModel()).getNumber().doubleValue();
			getConfiguration().setFrequencyCorrection(value);
			getConfiguration().setAutoPPMCorrectionEnabled(getAutoPPMCheckBox().isSelected());

			HydraSdrSampleRate rate = (HydraSdrSampleRate)getSampleRateCombo().getSelectedItem();
			if(rate != null)
			{
				getConfiguration().setSampleRate(rate.getRate());
			}

			int previousMode = getConfiguration().getGainMode();
			int mode = getGainModeCombo().getSelectedIndex();
			if(mode < 0)
			{
				mode = 0;
			}
			getConfiguration().setGainMode(mode);

			if(mode == 0 || mode == 1)
			{
				savePresetGain(getConfiguration(), mode, getMasterGainSlider().getValue());
			}
			else
			{
				/* Preserve saved custom values when first switching from a preset mode. */
				if(previousMode == 2)
				{
					getConfiguration().setVgaGain(getVgaGainSlider().getValue());
					getConfiguration().setLnaGain(getLnaGainSlider().getValue());
					getConfiguration().setRfGain(getRfGainSlider().getValue());
					getConfiguration().setMixerGain(getMixerGainSlider().getValue());
					getConfiguration().setFilterGain(getFilterGainSlider().getValue());
					getConfiguration().setLnaAgc(getLnaAgcCheckBox().isSelected());
					getConfiguration().setRfAgc(getRfAgcCheckBox().isSelected());
					getConfiguration().setMixerAgc(getMixerAgcCheckBox().isSelected());
					getConfiguration().setFilterAgc(getFilterAgcCheckBox().isSelected());
				}
			}
			getConfiguration().setBiasT(getBiasTCheckBox().isSelected());
			if(getRfPortCombo().getSelectedItem() instanceof Integer rfPort)
			{
				getConfiguration().setRfPort(rfPort);
			}
			saveConfiguration();
		}
	}

	@Override
	public void setTunerLockState(boolean locked)
	{
		getFrequencyPanel().updateControls();
		getSampleRateCombo().setEnabled(!locked);
		if(hasTuner() && getTuner().getController().isLockedSampleRate())
		{
			getSampleRateCombo().setToolTipText(
				"Sample Rate is locked. Disable decoding channels to unlock.");
		}
		else
		{
			getSampleRateCombo().setToolTipText("Select a sample rate for the tuner");
		}
	}

	private String getTunerInfo()
	{
		if(getDiscoveredTuner().getTunerStatus() == TunerStatus.ERROR)
		{
			return getDiscoveredTuner().getErrorMessage();
		}

		if(hasTuner())
		{
			StringBuilder sb = new StringBuilder();
			HydraSdrDeviceInfo info = getTuner().getController().getDeviceInfo();

			sb.append("<html><h3>HydraSDR Tuner</h3>");

			if(info != null)
			{
				sb.append("<b>Board: </b>").append(info.getBoardName()).append("<br>");
				sb.append("<b>Serial: </b>").append(info.getSerialNumber()).append("<br>");
				sb.append("<b>Firmware: </b>").append(info.getFirmwareVersion()).append("<br>");
				sb.append("<b>Part: </b>").append(info.getPartNumber()).append("<br>");
				sb.append("<b>Freq Range: </b>").append(info.getMinFrequency() / 1e6)
					.append(" - ").append(info.getMaxFrequency() / 1e6).append(" MHz<br>");
				sb.append("<b>Capabilities: </b>0x")
					.append(Integer.toHexString(info.getCapabilities())).append("<br>");

				float temp = getTuner().getController().getTemperature();
				if(!Float.isNaN(temp))
				{
					sb.append("<b>Temperature: </b>").append(String.format("%.1f", temp))
						.append(" C<br>");
				}
			}
			else
			{
				sb.append("Device info not available<br>");
			}

			/* Library version */
			if(HydraSdrNative.isLoaded())
			{
				int[] ver = HydraSdrNative.getLibVersion();
				if(ver != null && ver.length >= 3)
				{
					sb.append("<b>Library: </b>").append(ver[0]).append(".")
						.append(ver[1]).append(".").append(ver[2]).append("<br>");
				}
			}

			/* Performance stats */
			String stats = getTuner().getController().getPerformanceStats();
			sb.append("<br><b>--- Performance ---</b><br>");
			sb.append("<pre>").append(stats).append("</pre>");

			return sb.toString();
		}

		return null;
	}
}
