package com.disgusty.foxtalgia.ui.browser;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.InputType;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.TimePicker;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.Compat;
import com.disgusty.foxtalgia.ui.kit.ColorPicker;
import com.disgusty.foxtalgia.ui.kit.DialogBuilder;
import com.disgusty.foxtalgia.ui.kit.Field;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.util.Sdk;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate;
import org.mozilla.geckoview.GeckoSession.PromptDelegate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Web page dialogs and permission requests rendered with the active style. */
public final class Prompts implements PromptDelegate, PermissionDelegate, GeckoSession.MediaDelegate {
    public static final int REQUEST_FILE = 4101;
    public static final int REQUEST_PERMISSIONS = 4102;

    private final Activity activity;
    private final Kit k;
    private final I18n t;
    private GeckoResult<PromptResponse> pendingFile;
    private FilePrompt pendingFilePrompt;
    private PermissionDelegate.Callback pendingAndroid;

    public Prompts(Activity activity, Kit kit) {
        this.activity = activity;
        this.k = kit;
        this.t = I18n.get();
    }

    private String s(String key) {
        return t.text(key);
    }

    private static String host(String uri) {
        try {
            String h = Uri.parse(uri).getHost();
            return h == null ? uri : h;
        } catch (Exception e) {
            return uri;
        }
    }

    // ---------------------------------------------------------------- simple dialogs

    @Override
    public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession session, final AlertPrompt prompt) {
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        k.dialog().title(prompt.title).message(prompt.message)
                .positive(s("general.ok"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.dismiss());
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.dismiss());
                    }
                }).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession session, final ButtonPrompt prompt) {
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        k.dialog().title(prompt.title).message(prompt.message)
                .negative(s("general.cancel"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(ButtonPrompt.Type.NEGATIVE));
                    }
                })
                .positive(s("general.ok"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(ButtonPrompt.Type.POSITIVE));
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.dismiss());
                    }
                }).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onTextPrompt(GeckoSession session, final TextPrompt prompt) {
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        final Field f = k.field(null, false);
        if (prompt.defaultValue != null) f.text(prompt.defaultValue);
        k.dialog().title(prompt.title).message(prompt.message).view(f.view)
                .negative(s("general.cancel"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.dismiss());
                    }
                })
                .positive(s("general.ok"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(f.text()));
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.dismiss());
                    }
                }).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onAuthPrompt(GeckoSession session, final AuthPrompt prompt) {
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        LinearLayout box = k.vbox();
        final boolean onlyPassword = (prompt.authOptions.flags & AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0;
        final Field user = k.field(s("prompts.username"), false);
        final Field pass = k.field(s("prompts.password"), false);
        Compat.inputType(pass.edit, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (prompt.authOptions.username != null) user.text(prompt.authOptions.username);
        if (prompt.authOptions.password != null) pass.text(prompt.authOptions.password);
        if (!onlyPassword) box.addView(user.view, Kit.matchWrap());
        box.addView(pass.view, Kit.matchWrap());
        k.dialog().title(prompt.title != null ? prompt.title : s("prompts.authTitle")).message(prompt.message).view(box)
                .negative(s("general.cancel"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.dismiss());
                    }
                })
                .positive(s("prompts.signIn"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, onlyPassword ? prompt.confirm(pass.text()) : prompt.confirm(user.text(), pass.text()));
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.dismiss());
                    }
                }).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onChoicePrompt(GeckoSession session, final ChoicePrompt prompt) {
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        final List<ChoicePrompt.Choice> flat = new ArrayList<ChoicePrompt.Choice>();
        flatten(prompt.choices, flat, "");
        final List<String> labels = new ArrayList<String>();
        final List<ChoicePrompt.Choice> enabled = new ArrayList<ChoicePrompt.Choice>();
        for (ChoicePrompt.Choice ch : flat) {
            if (ch.separator || ch.disabled || ch.items != null) continue;
            enabled.add(ch);
            labels.add(ch.label);
        }
        CharSequence[] items = labels.toArray(new CharSequence[labels.size()]);
        DialogBuilder b = k.dialog().title(prompt.title).message(prompt.message);
        if (prompt.type == ChoicePrompt.Type.MULTIPLE) {
            final boolean[] checked = new boolean[enabled.size()];
            for (int i = 0; i < checked.length; i++) checked[i] = enabled.get(i).selected;
            b.multiChoice(items, checked, null)
                    .negative(s("general.cancel"), new Runnable() {
                        @Override
                        public void run() {
                            complete(r, prompt.dismiss());
                        }
                    })
                    .positive(s("general.ok"), new Runnable() {
                        @Override
                        public void run() {
                            List<ChoicePrompt.Choice> sel = new ArrayList<ChoicePrompt.Choice>();
                            for (int i = 0; i < checked.length; i++) if (checked[i]) sel.add(enabled.get(i));
                            complete(r, prompt.confirm(sel.toArray(new ChoicePrompt.Choice[sel.size()])));
                        }
                    });
        } else {
            int sel = -1;
            for (int i = 0; i < enabled.size(); i++) if (enabled.get(i).selected) sel = i;
            DialogBuilder.ItemListener l = new DialogBuilder.ItemListener() {
                @Override
                public void onItem(int index) {
                    complete(r, prompt.confirm(enabled.get(index)));
                }
            };
            if (prompt.type == ChoicePrompt.Type.MENU) b.items(items, l);
            else b.singleChoice(items, sel, l);
        }
        b.onDismiss(new Runnable() {
            @Override
            public void run() {
                complete(r, prompt.dismiss());
            }
        }).show();
        return r;
    }

    private static void flatten(ChoicePrompt.Choice[] choices, List<ChoicePrompt.Choice> out, String indent) {
        if (choices == null) return;
        for (ChoicePrompt.Choice ch : choices) {
            out.add(ch);
            if (ch.items != null) flatten(ch.items, out, indent + "  ");
        }
    }

    @Override
    public GeckoResult<PromptResponse> onColorPrompt(GeckoSession session, final ColorPrompt prompt) {
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        final ColorPicker picker = new ColorPicker(activity);
        int initial = 0xFF000000;
        try {
            if (prompt.defaultValue != null) initial = android.graphics.Color.parseColor(prompt.defaultValue);
        } catch (Exception ignored) {
            // keep black
        }
        picker.setColor(initial);
        k.dialog().title(prompt.title != null ? prompt.title : s("prompts.color")).view(picker)
                .negative(s("general.cancel"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.dismiss());
                    }
                })
                .positive(s("general.ok"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(String.format(Locale.US, "#%06x", picker.color() & 0xFFFFFF)));
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.dismiss());
                    }
                }).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onDateTimePrompt(GeckoSession session, final DateTimePrompt prompt) {
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        final Calendar cal = Calendar.getInstance();
        final int type = prompt.type;
        String def = prompt.defaultValue;
        try {
            if (def != null && def.length() >= 10 && type != DateTimePrompt.Type.TIME) {
                cal.set(Integer.parseInt(def.substring(0, 4)), Integer.parseInt(def.substring(5, 7)) - 1, Integer.parseInt(def.substring(8, 10)));
            } else if (def != null && type == DateTimePrompt.Type.MONTH && def.length() >= 7) {
                cal.set(Integer.parseInt(def.substring(0, 4)), Integer.parseInt(def.substring(5, 7)) - 1, 1);
            }
            int tpos = def == null ? -1 : def.indexOf(type == DateTimePrompt.Type.TIME ? ':' : 'T');
            if (def != null && type == DateTimePrompt.Type.TIME && def.length() >= 5) {
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(def.substring(0, 2)));
                cal.set(Calendar.MINUTE, Integer.parseInt(def.substring(3, 5)));
            } else if (def != null && type == DateTimePrompt.Type.DATETIME_LOCAL && tpos > 0 && def.length() >= tpos + 6) {
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(def.substring(tpos + 1, tpos + 3)));
                cal.set(Calendar.MINUTE, Integer.parseInt(def.substring(tpos + 4, tpos + 6)));
            }
        } catch (Exception ignored) {
            // malformed default: today
        }
        final boolean[] done = new boolean[1];
        if (type == DateTimePrompt.Type.TIME) {
            TimePickerDialog d = new TimePickerDialog(activity, new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker view, int h, int m) {
                    done[0] = true;
                    complete(r, prompt.confirm(String.format(Locale.US, "%02d:%02d", h, m)));
                }
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(activity));
            d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(android.content.DialogInterface dialog) {
                    if (!done[0]) complete(r, prompt.dismiss());
                }
            });
            d.show();
            return r;
        }
        DatePickerDialog d = new DatePickerDialog(activity, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, final int y, final int mo, final int day) {
                done[0] = true;
                if (type == DateTimePrompt.Type.MONTH) {
                    complete(r, prompt.confirm(String.format(Locale.US, "%04d-%02d", y, mo + 1)));
                } else if (type == DateTimePrompt.Type.WEEK) {
                    Calendar c2 = Calendar.getInstance(Locale.UK);
                    c2.set(y, mo, day);
                    complete(r, prompt.confirm(String.format(Locale.US, "%04d-W%02d", c2.get(Calendar.YEAR), c2.get(Calendar.WEEK_OF_YEAR))));
                } else if (type == DateTimePrompt.Type.DATETIME_LOCAL) {
                    new TimePickerDialog(activity, new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(TimePicker v2, int h, int m) {
                            complete(r, prompt.confirm(String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d", y, mo + 1, day, h, m)));
                        }
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(activity)).show();
                } else {
                    complete(r, prompt.confirm(String.format(Locale.US, "%04d-%02d-%02d", y, mo + 1, day)));
                }
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface dialog) {
                if (!done[0]) complete(r, prompt.dismiss());
            }
        });
        d.show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onFilePrompt(GeckoSession session, FilePrompt prompt) {
        GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        String mime = "*/*";
        if (prompt.mimeTypes != null && prompt.mimeTypes.length == 1 && prompt.mimeTypes[0].indexOf('/') > 0) mime = prompt.mimeTypes[0];
        i.setType(mime);
        if (prompt.type == FilePrompt.Type.MULTIPLE && Sdk.atLeast(18)) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            pendingFile = r;
            pendingFilePrompt = prompt;
            activity.startActivityForResult(Intent.createChooser(i, s("prompts.chooseFile")), REQUEST_FILE);
        } catch (Exception e) {
            pendingFile = null;
            r.complete(prompt.dismiss());
        }
        return r;
    }

    /** Called from the activity's onActivityResult. */
    public void onFileResult(int resultCode, Intent data) {
        GeckoResult<PromptResponse> r = pendingFile;
        FilePrompt p = pendingFilePrompt;
        pendingFile = null;
        pendingFilePrompt = null;
        if (r == null || p == null) return;
        if (resultCode != Activity.RESULT_OK || data == null) {
            r.complete(p.dismiss());
            return;
        }
        List<Uri> uris = new ArrayList<Uri>();
        if (Sdk.atLeast(16) && data.getClipData() != null) Api16.clip(data, uris);
        if (uris.isEmpty() && data.getData() != null) uris.add(data.getData());
        if (uris.isEmpty()) r.complete(p.dismiss());
        else if (uris.size() == 1) r.complete(p.confirm(activity, uris.get(0)));
        else r.complete(p.confirm(activity, uris.toArray(new Uri[uris.size()])));
    }

    @TargetApi(16)
    private static final class Api16 {
        static void clip(Intent data, List<Uri> out) {
            android.content.ClipData cd = data.getClipData();
            for (int i = 0; i < cd.getItemCount(); i++) if (cd.getItemAt(i).getUri() != null) out.add(cd.getItemAt(i).getUri());
        }
    }

    @Override
    public GeckoResult<PromptResponse> onPopupPrompt(GeckoSession session, final PopupPrompt prompt) {
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        String target = prompt.targetUri == null ? "" : prompt.targetUri;
        k.dialog().title(s("prompts.popupTitle")).message(t.text("prompts.popupMessage", "url", target))
                .negative(s("prompts.block"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(AllowOrDeny.DENY));
                    }
                })
                .positive(s("prompts.allow"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(AllowOrDeny.ALLOW));
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(AllowOrDeny.DENY));
                    }
                }).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onBeforeUnloadPrompt(GeckoSession session, final BeforeUnloadPrompt prompt) {
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        k.dialog().title(s("prompts.leaveTitle")).message(s("prompts.leaveMessage"))
                .negative(s("prompts.stay"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(AllowOrDeny.DENY));
                    }
                })
                .positive(s("prompts.leave"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(AllowOrDeny.ALLOW));
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(AllowOrDeny.DENY));
                    }
                }).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onRepostConfirmPrompt(GeckoSession session, final RepostConfirmPrompt prompt) {
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        k.dialog().title(s("prompts.repostTitle")).message(s("prompts.repostMessage"))
                .negative(s("general.cancel"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(AllowOrDeny.DENY));
                    }
                })
                .positive(s("prompts.resend"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(AllowOrDeny.ALLOW));
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        complete(r, prompt.confirm(AllowOrDeny.DENY));
                    }
                }).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onSharePrompt(GeckoSession session, SharePrompt prompt) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        StringBuilder sb = new StringBuilder();
        if (prompt.text != null) sb.append(prompt.text);
        if (prompt.uri != null) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(prompt.uri);
        }
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        if (prompt.title != null) i.putExtra(Intent.EXTRA_SUBJECT, prompt.title);
        try {
            activity.startActivity(Intent.createChooser(i, s("browser.share")));
            return GeckoResult.fromValue(prompt.confirm(SharePrompt.Result.SUCCESS));
        } catch (Exception e) {
            return GeckoResult.fromValue(prompt.confirm(SharePrompt.Result.FAILURE));
        }
    }

    @Override
    public GeckoResult<PromptResponse> onLoginSave(GeckoSession session, final AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption> request) {
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        if (request.options.length == 0) return null;
        final org.mozilla.geckoview.Autocomplete.LoginSaveOption opt = request.options[0];
        k.dialog().title(s("prompts.saveLoginTitle")).message(t.text("prompts.saveLoginMessage", "site", host(opt.value.origin)))
                .negative(s("prompts.notNow"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, request.dismiss());
                    }
                })
                .positive(s("prompts.save"), new Runnable() {
                    @Override
                    public void run() {
                        complete(r, request.confirm(opt));
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        complete(r, request.dismiss());
                    }
                }).show();
        return r;
    }

    @Override
    public GeckoResult<PromptResponse> onLoginSelect(GeckoSession session, final AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption> request) {
        if (request.options.length == 0) return null;
        final GeckoResult<PromptResponse> r = new GeckoResult<PromptResponse>();
        CharSequence[] names = new CharSequence[request.options.length];
        for (int i = 0; i < names.length; i++) {
            String u = request.options[i].value.username;
            names[i] = u == null || u.length() == 0 ? s("prompts.noUsername") : u;
        }
        k.dialog().title(s("prompts.chooseLogin")).items(names, new com.disgusty.foxtalgia.ui.kit.DialogBuilder.ItemListener() {
            @Override
            public void onItem(int index) {
                complete(r, request.confirm(request.options[index]));
            }
        }).onDismiss(new Runnable() {
            @Override
            public void run() {
                complete(r, request.dismiss());
            }
        }).show();
        return r;
    }

    private static void complete(GeckoResult<PromptResponse> r, PromptResponse response) {
        try {
            r.complete(response);
        } catch (IllegalStateException ignored) {
            // already completed (button followed by dismiss)
        }
    }

    // ---------------------------------------------------------------- permissions

    @Override
    public GeckoResult<Integer> onContentPermissionRequest(GeckoSession session, ContentPermission perm) {
        String key;
        switch (perm.permission) {
            case PERMISSION_GEOLOCATION:
                key = "permissions.location";
                break;
            case PERMISSION_DESKTOP_NOTIFICATION:
                key = "permissions.notifications";
                break;
            case PERMISSION_PERSISTENT_STORAGE:
                key = "permissions.storage";
                break;
            case PERMISSION_XR:
                key = "permissions.xr";
                break;
            case PERMISSION_MEDIA_KEY_SYSTEM_ACCESS:
                key = "permissions.drm";
                break;
            case PERMISSION_STORAGE_ACCESS:
                key = "permissions.storageAccess";
                break;
            case PERMISSION_LOCAL_DEVICE_ACCESS:
            case PERMISSION_LOCAL_NETWORK_ACCESS:
                key = "permissions.localNetwork";
                break;
            case PERMISSION_AUTOPLAY_AUDIBLE:
            case PERMISSION_AUTOPLAY_INAUDIBLE:
                return GeckoResult.fromValue(App.get().settings.autoplay() || perm.permission == PERMISSION_AUTOPLAY_INAUDIBLE
                        ? ContentPermission.VALUE_ALLOW : ContentPermission.VALUE_DENY);
            case PERMISSION_TRACKING:
                return GeckoResult.fromValue(ContentPermission.VALUE_PROMPT);
            default:
                return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
        }
        return ask(t.text(key, "site", host(perm.uri)));
    }

    private GeckoResult<Integer> ask(String message) {
        final GeckoResult<Integer> r = new GeckoResult<Integer>();
        final boolean[] answered = new boolean[1];
        k.dialog().icon(Ic.SECURITY).title(s("permissions.title")).message(message)
                .negative(s("prompts.block"), new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        r.complete(ContentPermission.VALUE_DENY);
                    }
                })
                .positive(s("prompts.allow"), new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        r.complete(ContentPermission.VALUE_ALLOW);
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        if (!answered[0]) r.complete(ContentPermission.VALUE_PROMPT);
                    }
                }).show();
        return r;
    }

    @Override
    public void onAndroidPermissionsRequest(GeckoSession session, String[] permissions, Callback callback) {
        if (!Sdk.atLeast(23) || permissions == null) {
            callback.grant();
            return;
        }
        pendingAndroid = callback;
        Api23.request(activity, permissions);
    }

    public void onPermissionsResult(String[] permissions, int[] results) {
        Callback cb = pendingAndroid;
        pendingAndroid = null;
        if (cb == null) return;
        boolean all = results.length > 0;
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) all = false;
        if (all) cb.grant();
        else cb.reject();
    }

    @TargetApi(23)
    private static final class Api23 {
        static void request(Activity a, String[] perms) {
            a.requestPermissions(perms, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onMediaPermissionRequest(GeckoSession session, String uri, final MediaSource[] video, final MediaSource[] audio, final MediaCallback callback) {
        String what = video != null && audio != null ? "permissions.cameraMic" : video != null ? "permissions.camera" : "permissions.mic";
        final boolean[] answered = new boolean[1];
        k.dialog().icon(Ic.VIDEOCAM).title(s("permissions.title")).message(t.text(what, "site", host(uri)))
                .negative(s("prompts.block"), new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        callback.reject();
                    }
                })
                .positive(s("prompts.allow"), new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        callback.grant(video != null && video.length > 0 ? video[0] : null, audio != null && audio.length > 0 ? audio[0] : null);
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        if (!answered[0]) callback.reject();
                    }
                }).show();
    }

    @Override
    public void onRecordingStatusChanged(GeckoSession session, RecordingDevice[] devices) {
    }

    /** Asks for WRITE_EXTERNAL_STORAGE on Android 6-9 before saving a download. */
    public static boolean needsStoragePermission(Activity a) {
        return Sdk.atLeast(23) && !Sdk.atLeast(29) && Api23b.missing(a);
    }

    @TargetApi(23)
    private static final class Api23b {
        static boolean missing(Activity a) {
            if (a.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return false;
            a.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSIONS + 1);
            return true;
        }
    }

}
