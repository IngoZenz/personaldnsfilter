package dnsfilter.android.dnsserverconfig.widget;


import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigBaseEntry.CHAR_LINE_COMMENTED;
import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry.EMPTY_STRING;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import dnsfilter.DNSServer;
import dnsfilter.android.R;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigBaseEntry;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigCommentedEntry;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry;

public class DNSListAdapter extends ArrayAdapter<DNSServerConfigBaseEntry> implements DNSServerConfigEntryView.EditEventsListener {

    private static final int DEFAULT_DNS_TIMEOUT = 15000;

    private final Animation progressBarAnim;
    private final String serverIsUnreachableMessage;

    private final Dialog testResultDialog;
    private final ImageView testResultImage;
    private final TextView testResultText;

    private final ExecutorService testTasksPool;
    private final Handler handler = new Handler();

    private final DNSServerConfigEntryView dnsServerConfigEntryView;

    public DNSListAdapter(Context context, List<DNSServerConfigBaseEntry> objects, ExecutorService executor) {
        super(context, 0, objects);

        this.dnsServerConfigEntryView = new DNSServerConfigEntryView(context, this);
        this.serverIsUnreachableMessage = context.getString(R.string.serverUnreachable);

        this.testResultDialog = new Dialog(context);
        this.testResultDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dnsentrytestresult, null);
        this.testResultDialog.setContentView(dialogView);
        this.testResultImage = dialogView.findViewById(R.id.resultIconImageView);
        this.testResultText = dialogView.findViewById(R.id.resultTextView);

        this.progressBarAnim = AnimationUtils.loadAnimation(context, R.anim.progress_rotation);
        this.progressBarAnim.setRepeatCount(Animation.INFINITE);

        this.testTasksPool = executor;
    }

    public void changeCommentedLinesVisibility(boolean isVisible) {
        boolean updateList = false;
        for (int i = 0; i < this.getObjectsCount(); i++) {
            DNSServerConfigBaseEntry entry = this.getItem(i);
            if (entry instanceof DNSServerConfigCommentedEntry && ((DNSServerConfigCommentedEntry) entry).isVisible() != isVisible) {
                ((DNSServerConfigCommentedEntry) entry).setVisible(isVisible);
                updateList = true;
            }
        }

        if (updateList) {
            this.notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        return super.getCount() + 1;
    }

    public int getObjectsCount() {
        return super.getCount();
    }

    @Override
    public int getItemViewType(int position) {
        if (position > super.getCount() - 1) {
            return 2;
        } else if (getItem(position) instanceof DNSServerConfigCommentedEntry) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        switch (getItemViewType(position)) {
            case 2:
                convertView = getAddButton(parent);
                break;
            case 1:
                convertView = getCommentView(position, convertView, parent);
                break;
            case 0:
                convertView = getItemView(position, convertView, parent);
                break;
        }

        return convertView;
    }

    private View getAddButton(ViewGroup parent) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dnsserverconfigentrylistnewitem, parent, false);
        view.findViewById(R.id.addNewItemButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DNSServerConfigEntry newEntry = new DNSServerConfigEntry();
                add(newEntry);
                dnsServerConfigEntryView.showEntry(newEntry, true);
            }
        });
        return view;
    }

    private View getCommentView(int position, View convertView, ViewGroup parent) {
        DNSServerConfigCommentedEntry entry = (DNSServerConfigCommentedEntry) getItem(position);

        DNSServerCommentEntryViewHolder holder;

        if (!entry.isVisible()) {
            return new View(this.getContext());
        }

        if (convertView == null || !(convertView.getTag() instanceof DNSServerCommentEntryViewHolder)) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.dnsserverconfigentrylistcommentitem, parent, false);

            holder = new DNSServerCommentEntryViewHolder();
            setupNewCommentHolder(holder, convertView);
            convertView.setTag(holder);
        } else {
            holder = (DNSServerCommentEntryViewHolder) convertView.getTag();
        }

        holder.dnsServerCommentEntry = entry;
        holder.commentView.setText(entry.toString().replace(CHAR_LINE_COMMENTED, EMPTY_STRING));
        holder.containter.setEnabled(false);

        return convertView;
    }

    private View getItemView(int position, View convertView, ViewGroup parent) {
        DNSServerConfigEntry entry = (DNSServerConfigEntry) getItem(position);

        DNSServerConfigEntryViewHolder holder;

        if (convertView == null || !(convertView.getTag() instanceof DNSServerConfigEntryViewHolder)) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.dnsserverconfigentrylistitem, parent, false);

            holder = new DNSServerConfigEntryViewHolder();
            setupNewViewHolder(holder, convertView);
            convertView.setTag(holder);

        } else {
            holder = (DNSServerConfigEntryViewHolder) convertView.getTag();
        }

        holder.dnsServerConfigEntry = entry;

        holder.protocolView.setText(entry.getProtocol().toString());
        holder.ipView.setText(entry.getIp());
        holder.portView.setText(entry.getPort());
        holder.isActiveEntryCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    entry.setIsActive(isChecked);
                    holder.root.setEnabled(entry.getIsActive());
                }
        );
        holder.isActiveEntryCheckbox.setChecked(entry.getIsActive());
        holder.setupTestButtons();

        holder.testEntryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                entry.getTestResult().setTestState(DNSServerConfigEntryTestState.STARTED);
                notifyDataSetChanged();
                Runnable testTask = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            long result = DNSServer.getInstance()
                                    .createDNSServer(new DNSServerConfigEntry(
                                            entry.getIp(),
                                            entry.getPort(),
                                            entry.getProtocol(),
                                            entry.getEndpoint(),
                                            true
                                    ).toString(), DEFAULT_DNS_TIMEOUT)
                                    .testDNS(5);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    entry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.SUCCESS, result));
                                    notifyDataSetChanged();
                                }
                            });
                        } catch (IOException e) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    String errorMessage = e.getMessage();
                                    if (errorMessage == null) {
                                        errorMessage = serverIsUnreachableMessage;
                                    }
                                    entry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.FAIL, errorMessage));
                                    notifyDataSetChanged();
                                }
                            });
                        }
                    }
                };
                testTasksPool.execute(testTask);
            }
        });
        holder.testEntryResultFailure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testResultImage.setImageResource(R.drawable.ic_exclamation_circle);
                testResultText.setText(v.getContext().getString(R.string.testDNSResultFailure, entry.getTestResult().getMessage()));
                testResultDialog.show();
                holder.dnsServerConfigEntry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.NOT_STARTED));
                notifyDataSetChanged();
            }
        });
        holder.testEntryResultSuccess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testResultImage.setImageResource(R.drawable.ic_check_circle);
                testResultText.setText(v.getContext().getString(R.string.testDNSResultSuccess, entry.getTestResult().getPerf()));
                testResultDialog.show();
                holder.dnsServerConfigEntry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.NOT_STARTED));
                notifyDataSetChanged();
            }
        });

        holder.root.setEnabled(entry.getIsActive());

        return convertView;
    }

    private void setupNewCommentHolder(DNSServerCommentEntryViewHolder holder, View convertView) {
        findCommentEntryViews(holder, convertView);
    }

    private void findCommentEntryViews(DNSServerCommentEntryViewHolder holder, View convertView) {
        holder.commentView = convertView.findViewById(R.id.commentText);
        holder.containter = convertView.findViewById(R.id.container);
    }

    private void setupNewViewHolder(DNSServerConfigEntryViewHolder holder, View convertView) {
        findEntryViews(holder, convertView);

        holder.editEntryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dnsServerConfigEntryView.showEntry(holder.dnsServerConfigEntry, false);
            }
        });
    }

    private void findEntryViews(DNSServerConfigEntryViewHolder holder, View convertView) {
        holder.root = convertView.findViewById(R.id.container);
        holder.protocolView = convertView.findViewById(R.id.protocolText);
        holder.ipView = convertView.findViewById(R.id.ipText);
        holder.portView = convertView.findViewById(R.id.portText);
        holder.editEntryButton = convertView.findViewById(R.id.editEntryButton);
        holder.isActiveEntryCheckbox = convertView.findViewById(R.id.isActiveEntryCheckbox);

        holder.testEntryButton = convertView.findViewById(R.id.testEntryButton);
        holder.testEntryProgressBar = convertView.findViewById(R.id.testEntryProgressBar);
        holder.testEntryResultFailure = convertView.findViewById(R.id.testEntryButtonResultFailure);
        holder.testEntryResultSuccess = convertView.findViewById(R.id.testEntryButtonResultSuccess);

        holder.progressBarAnim = this.progressBarAnim;
    }

    @Override
    public void onApplyChanges() {
        notifyDataSetChanged();
    }

    @Override
    public void onNewCancelled(DNSServerConfigEntry entry) {
        remove(entry);
    }

    @Override
    public void onDeleteItem(DNSServerConfigEntry entry) {
        remove(entry);
    }

    static class DNSServerConfigEntryViewHolder {
        DNSServerConfigEntry dnsServerConfigEntry;
        TextView protocolView;
        TextView ipView;
        TextView portView;
        CheckBox isActiveEntryCheckbox;
        ImageButton editEntryButton;
        RelativeLayout root;

        ImageButton testEntryButton;
        ImageButton testEntryProgressBar;
        ImageButton testEntryResultSuccess;
        ImageButton testEntryResultFailure;

        Animation progressBarAnim;

        private void setupTestButtons() {
            DNSServerConfigEntryTestState testState = dnsServerConfigEntry.getTestResult().getTestState();
            if (testState == null) {
                testEntryButton.setVisibility(View.VISIBLE);
                testEntryProgressBar.clearAnimation();
                testEntryProgressBar.setVisibility(View.INVISIBLE);
                testEntryResultSuccess.setVisibility(View.INVISIBLE);
                testEntryResultFailure.setVisibility(View.INVISIBLE);
                return;
            }
            switch (testState) {
                case STARTED:
                    testEntryButton.setVisibility(View.INVISIBLE);
                    testEntryProgressBar.startAnimation(progressBarAnim);
                    testEntryProgressBar.setVisibility(View.VISIBLE);
                    testEntryResultSuccess.setVisibility(View.INVISIBLE);
                    testEntryResultFailure.setVisibility(View.INVISIBLE);
                    break;
                case FAIL:
                    testEntryButton.setVisibility(View.INVISIBLE);
                    testEntryProgressBar.clearAnimation();
                    testEntryProgressBar.setVisibility(View.INVISIBLE);
                    testEntryResultSuccess.setVisibility(View.INVISIBLE);
                    testEntryResultFailure.setVisibility(View.VISIBLE);
                    break;
                case SUCCESS:
                    testEntryButton.setVisibility(View.INVISIBLE);
                    testEntryProgressBar.clearAnimation();
                    testEntryProgressBar.setVisibility(View.INVISIBLE);
                    testEntryResultSuccess.setVisibility(View.VISIBLE);
                    testEntryResultFailure.setVisibility(View.INVISIBLE);
                    break;
                default:
                    testEntryButton.setVisibility(View.VISIBLE);
                    testEntryProgressBar.clearAnimation();
                    testEntryProgressBar.setVisibility(View.INVISIBLE);
                    testEntryResultSuccess.setVisibility(View.INVISIBLE);
                    testEntryResultFailure.setVisibility(View.INVISIBLE);
                    break;
            }
        }
    }

    static class DNSServerCommentEntryViewHolder {
        DNSServerConfigCommentedEntry dnsServerCommentEntry;
        TextView commentView;
        ViewGroup containter;
    }
}
