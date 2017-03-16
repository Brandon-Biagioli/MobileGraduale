package biagioli.brandon.mobilegraduale;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by Brandon Biagioli on 4/11/2016.
 */
public class GregorianChantView extends ChantView {
    protected int measuredHeight = 400;//this is updated as the chant is drawn, and used by onMeasure()
    protected boolean drawnYet = false;

    protected enum Mode { ONE,TWO,THREE,FOUR,FIVE,SIX,SEVEN,EIGHT }
    protected enum Clef { DO,FA }
    protected enum NoteFlag { //these flags are all used for formatting notes in one way or another
        DOT,RHOMBUS,PORRECTUS,SECOND_PORRECTUS,THIRD_PORRECTUS,V_EPISEMA,H_EPISEMA,QUILISMA,LIQUESCENT,
        SCANDICUS,TORCULUS,SECOND_TORCULUS,THIRD_TORCULUS,PEAK,ASCENDING,FIRST_DESCENDING,SECOND_DESCENDING,
        JUMP,STACKED_ASCENDING,QUARTER_BAR,HALF_BAR,FULL_BAR,DOUBLE_BAR,REPEATED,CLIVIS,SECOND_CLIVIS
    }

    protected Mode mode;//information about the chant being displayed
    protected EnumSet<NoteFlag> faClefFlags;
    protected Paint textPaint;//Paint objects for various things that need to be displayed
    protected Paint staffPaint;
    protected Paint notePaint;
    protected Paint episemaPaint;
    protected Path clefPath;//objects for drawing the clef and notes; these get reused frequently
    protected Path notePath;
    protected RectF arcRectF;
    protected ArrayList<LinkedList<ChantSyllable>> syllables; //data for the chant being displayed
                                /*normally, there will only be one LinkedList of syllables. However,
                                * when a chant has sections with different clefs, I will break the
                                * list of syllables into multiple lists, one list per section*/
    protected int currentSection;
    protected ArrayList<Integer> clefLine;
    protected ArrayList<Clef> clef;
    protected LinkedList<String> errorMessages;//error messages
    protected Paint errorPaint;

    protected static final float SQRT_TWO = (float)Math.sqrt(2);//a useful number for geometry

    protected static final int STAFF_START_X = 10; //top-right corner of the first staff lines
    protected static final int STAFF_START_Y = 70; //top-right corner of the first staff lines
    protected static final int STAFF_END_MARGIN = 5; //the margin after the staff lines on the right
    protected static final int STAFF_SPACE = 48; //the space between horizontal lines in the staff

    protected static final int BASE_NOTE_OFFSET = 20; //constants used for getting the spacing of notes/words right
    protected static final int TEXT_START_OFFSET = 40; //generally, these are the offsets used AFTER an item before drawing the next
    protected static final int SYLLABLE_OFFSET = 25;
    protected static final int WORD_OFFSET = 45;
    protected static final int CLEF_OFFSET = 80;
    protected static final int BASE_EPISEMA_HEIGHT = STAFF_SPACE/4-3;

    protected ChantNote previousNote; //while notes are being drawn, this keeps track of the previous note

    protected class ChantSyllable {
        protected String text;
        ChantNote[] notes;
        protected boolean wordEnd;
        protected boolean hasFlat;
        protected boolean hasNeutral;
        protected int width;

        protected ChantSyllable(String text, ChantNote[] notes, boolean wordEnd, boolean hasFlat, boolean hasNeutral, int width) {
            this.text = text;
            this.notes = notes;
            this.wordEnd = wordEnd;
            this.hasFlat = hasFlat;
            this.hasNeutral = hasNeutral;
            this.width = width;
        }
    }

    protected class ChantNote {
        protected float value;//1 to 4 correspond to staff lines, and intermediate, higher, and
                                //lower values are possible
        protected EnumSet<NoteFlag> flags;//information relevant to drawing the note
        protected int offset; //the distance on the canvas that the next note is offset from this one
        protected int episema_height;

        protected ChantNote(float value, EnumSet<NoteFlag> flags) {
            this.value = value;
            this.flags = flags;
            this.offset = BASE_NOTE_OFFSET;
            episema_height = BASE_EPISEMA_HEIGHT;
        }

        //This method uses the flags to adjust the note's offset; it is not called until
        // it is certain that the flags will not be adjusted further.
        protected int adjustOffset(ChantNote nextNote) {
            if (nextNote != null &&
                    (nextNote.flags.contains(NoteFlag.LIQUESCENT) || nextNote.flags.contains(NoteFlag.STACKED_ASCENDING))) {
                offset = 0;
            } else if (flags.contains(NoteFlag.DOT)) {
                offset = BASE_NOTE_OFFSET + 15;
            } else if (flags.contains(NoteFlag.PORRECTUS)) {
                offset = (int)(BASE_NOTE_OFFSET * 2.5f);
            } else if (flags.contains(NoteFlag.REPEATED) || flags.contains(NoteFlag.STACKED_ASCENDING)) {
                offset = BASE_NOTE_OFFSET + 5;
            }
            return offset;
        }
    }

    public GregorianChantView(Context context, int chantID) {
        super(context);

        textPaint = new Paint();
        textPaint.setTextSize(60);
        staffPaint = new Paint();
        staffPaint.setStrokeWidth(2);
        episemaPaint = new Paint();
        episemaPaint.setStrokeWidth(4);
        notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        notePaint.setStyle(Paint.Style.FILL);
        notePath = new Path();
        clefPath = new Path();
        arcRectF = new RectF();
        errorMessages = new LinkedList<>();
        errorPaint = new Paint();
        errorPaint.setColor(Color.RED);
        errorPaint.setTextSize(40);

        parseText(getContext().getString(chantID));
    }

    protected void parseText(String text) {
        syllables = new ArrayList<>();
        currentSection = -1;
        ChantNote[] syllableNotesArray;
        clef = new ArrayList<>();
        clefLine = new ArrayList<>();
        Iterator<ChantNote> it;
        EnumSet<NoteFlag> flags;
        LinkedList<ChantNote> syllableNotes = new LinkedList<>();

        String[] tokens = text.split(" ");
        String[] parts;
        String[] inner;
        String lead;

        boolean wordEnd = true;
        boolean hasFlat = false;
        boolean hasNeutral = false;

        for(String token : tokens) {
            parts = token.split("[\\(\\)]");
            //parts should now be an array of exactly two elements, if the input is correctly formatted
            //correct formatting is lead(inner)
            //the exception is a "syllable" without notes, such as the 'Ps.' that introduces a psalm verse
            lead = parts[0];
            if (parts.length == 1) {
                syllables.get(currentSection).add(new ChantSyllable(lead, null, true, false, false, 0)); //this is a syllable without notes
            } else {
                inner = parts[1].split("[,]");

                switch (lead) {
                    case "MODE":
                        switch (inner[0]) {
                            case "one":
                                mode = Mode.ONE;
                                break;
                            case "two":
                                mode = Mode.TWO;
                                break;
                            case "three":
                                mode = Mode.THREE;
                                break;
                            case "four":
                                mode = Mode.FOUR;
                                break;
                            case "five":
                                mode = Mode.FIVE;
                                break;
                            case "six":
                                mode = Mode.SIX;
                                break;
                            case "seven":
                                mode = Mode.SEVEN;
                                break;
                            case "eight":
                                mode = Mode.EIGHT;
                                break;
                            default:
                                errorMessages.add("Error in resource string: \"" + inner[0] + "\" is an invalid mode\n");
                                break;
                        }
                        break;
                    case "CLEF":
                        currentSection++;
                        syllables.add(new LinkedList<ChantSyllable>());
                        switch (inner[0]) {
                            case "do":
                                clef.add(Clef.DO);
                                break;
                            case "fa":
                                clef.add(Clef.FA);
                                break;
                            default:
                                errorMessages.add("Error in resource string: \"" + inner[0] + "\" is an invalid clef\n");
                                break;
                        }
                        clefLine.add(Integer.parseInt(inner[1]));
                        break;
                    case "BAR":
                        if (currentSection < 0) {
                            errorMessages.add("Error in resource string: a clef is needed before any notes\n");
                            break;
                        }
                        //a bar line is treated like a special kind of note
                        String barText = " ";
                        flags = EnumSet.noneOf(NoteFlag.class);
                        //"barValue" will be ignored, but a ChantNote can't be initialized without it
                        float barValue = 4f;
                        switch (inner[0]) {
                            case "quarter":
                                flags.add(NoteFlag.QUARTER_BAR);
                                break;
                            case "half":
                                flags.add(NoteFlag.HALF_BAR);
                                break;
                            case "full":
                                flags.add(NoteFlag.FULL_BAR);
                                break;
                            case "double":
                                flags.add(NoteFlag.DOUBLE_BAR);
                                break;
                            default:
                                errorMessages.add("Error in resource string: \"" + inner[0] + "\" is an invalid bar\n");
                                break;
                        }
                        syllableNotes.add(new ChantNote(barValue, flags));

                        syllableNotesArray = new ChantNote[syllableNotes.size()];
                        syllableNotes.toArray(syllableNotesArray);
                        syllables.get(currentSection).add(new ChantSyllable(barText, syllableNotesArray, true, false, false, BASE_NOTE_OFFSET));

                        //reset the list of notes, so it can be used again
                        it = syllableNotes.iterator();
                        while (it.hasNext()) {
                            it.next();
                            it.remove();
                        }
                        break;
                    default://if lead wasn't MODE, CLEF, or BAR, it's a regular note, and lead was the syllable
                        if (currentSection < 0) {
                            errorMessages.add("Error in resource string: a clef is needed before any notes\n");
                            break;
                        }
                        lead = lead.replace('_', ' ');//the resource string can't have ' ' in it, that would confuse the parser
                        String syllableText = lead;
                        if (syllableText.charAt(syllableText.length() - 1) == '-') {
                            wordEnd = false;
                        }
                        int len = inner.length;
                        flags = EnumSet.noneOf(NoteFlag.class);
                        ChantNote previousNote = null;
                        boolean addNote = false;
                        float noteValue;

                        //start parsing actual notes
                        for (int i = 0; i < len; i++) {
                            char checkLastChar = inner[i].charAt(inner[i].length() - 1);
                            noteValue = clefLine.get(currentSection);//the base value
                            if (clef.get(currentSection) == Clef.FA) {
                                noteValue -= 1.5f;//the fa clef is different; the base value must be do
                            }
                            if (checkLastChar == '-') {
                                //adjust the note value to be in a lower octave
                                noteValue -= 3.5f;
                                //trim the '-' from the string, so the upcoming switch() statement can
                                //parse the note correctly
                                inner[i] = inner[i].substring(0, inner[i].length() - 1);
                            } else if (checkLastChar == '+') {
                                //adjust the note value to be in a higher octave
                                noteValue += 3.5f;
                                //trim the '+' from the string, so the upcoming switch() statement can
                                //parse the note correctly
                                inner[i] = inner[i].substring(0, inner[i].length() - 1);
                            }

                            switch (inner[i]) {
                                case "do":
                                    /*noteValue += 0; no need to actually execute this code*/
                                    addNote = true;
                                    break;
                                case "re":
                                    noteValue += 0.5f;
                                    addNote = true;
                                    break;
                                case "mi":
                                    noteValue += 1;
                                    addNote = true;
                                    break;
                                case "fa":
                                    noteValue += 1.5f;
                                    addNote = true;
                                    break;
                                case "sol":
                                    noteValue += 2;
                                    addNote = true;
                                    break;
                                case "la":
                                    noteValue += 2.5f;
                                    addNote = true;
                                    break;
                                case "te":
                                    noteValue += 3;
                                    hasFlat = true;
                                    addNote = true;
                                    break;
                                case "ti":
                                    noteValue += 3;
                                    addNote = true;
                                    break;
                                case "neut":
                                    hasNeutral = true;
                                    break;
                                case "liq":
                                    flags.add(NoteFlag.LIQUESCENT);
                                    break;
                                case "dot":
                                    flags.add(NoteFlag.DOT);
                                    break;
                                case "quil":
                                    flags.add(NoteFlag.QUILISMA);
                                    break;
                                case "por":
                                    flags.add(NoteFlag.PORRECTUS);
                                    break;
                                case "torc":
                                    flags.add(NoteFlag.TORCULUS);
                                    break;
                                case "scand":
                                    flags.add(NoteFlag.SCANDICUS);
                                    break;
                                case "cliv":
                                    flags.add(NoteFlag.CLIVIS);
                                    break;
                                case "v_epi":
                                    flags.add(NoteFlag.V_EPISEMA);
                                    break;
                                case "h_epi":
                                    flags.add(NoteFlag.H_EPISEMA);
                                    break;
                                default:
                                    errorMessages.add("Error in resource string: \"" + inner[i] + "\" is not a recognized note or flag");
                                    break;
                            }
                            if (addNote) {
                                //check flags from the previous note, add determine whether any flags
                                //need to be changed based on the relationships between the current note
                                //and the previous note
                                if (previousNote != null) {
                                    //flag the second and third notes of a torculus and of a porrectus
                                    if (previousNote.flags.contains(NoteFlag.TORCULUS)) {
                                        flags.add(NoteFlag.SECOND_TORCULUS);
                                    }
                                    if (previousNote.flags.contains(NoteFlag.SECOND_TORCULUS)) {
                                        flags.add(NoteFlag.THIRD_TORCULUS);
                                    }
                                    if (previousNote.flags.contains(NoteFlag.PORRECTUS)) {
                                        flags.add(NoteFlag.SECOND_PORRECTUS);
                                    }
                                    if (previousNote.flags.contains(NoteFlag.SECOND_PORRECTUS)) {
                                        flags.add(NoteFlag.THIRD_PORRECTUS);
                                    }
                                    //each note that is different than the previous should get the
                                    //JUMP flag if it is more than a step different
                                    if (noteValue - previousNote.value > 0.5f || noteValue - previousNote.value < -0.5f) {
                                        flags.add(NoteFlag.JUMP);
                                        //if the previous note was the last note of a porrectus, it should not
                                        //be STACKED_ASCENDING
                                        if (previousNote.flags.contains(NoteFlag.THIRD_PORRECTUS)) {
                                            previousNote.flags.remove(NoteFlag.STACKED_ASCENDING);
                                        }
                                    }
                                    if (previousNote.value > noteValue) {
                                        //if the previous note was ascending, and this note is descending,
                                        //flag the previous note as a peak
                                        if (previousNote.flags.contains(NoteFlag.ASCENDING)) {
                                            previousNote.flags.add(NoteFlag.PEAK);
                                        }
                                        //flag the second note of a clivis
                                        if (previousNote.flags.contains(NoteFlag.CLIVIS)) {
                                            flags.add(NoteFlag.SECOND_CLIVIS);
                                        }
                                        //if there are at least three descending notes in a row, each after the
                                        //first needs the RHOMBUS flag
                                        if (!flags.contains(NoteFlag.SCANDICUS)) {
                                            if (previousNote.flags.contains(NoteFlag.RHOMBUS) &&
                                                !previousNote.flags.contains(NoteFlag.DOT) &&
                                                    !flags.contains(NoteFlag.TORCULUS)) {
                                                flags.add(NoteFlag.RHOMBUS);
                                            } else if (previousNote.flags.contains(NoteFlag.SECOND_DESCENDING) &&
                                                    !previousNote.flags.contains(NoteFlag.DOT) &&
                                                    !previousNote.flags.contains(NoteFlag.THIRD_TORCULUS) &&
                                                    !flags.contains(NoteFlag.TORCULUS)) {
                                                previousNote.flags.remove(NoteFlag.SECOND_DESCENDING);
                                                previousNote.flags.add(NoteFlag.RHOMBUS);
                                                flags.add(NoteFlag.RHOMBUS);
                                            } else {
                                                if (!previousNote.flags.contains(NoteFlag.STACKED_ASCENDING)) {
                                                    previousNote.flags.add(NoteFlag.FIRST_DESCENDING);
                                                }
                                                flags.add(NoteFlag.SECOND_DESCENDING);
                                            }
                                        }
                                    } else if (previousNote.value < noteValue) {
                                        //each ascending note is flagged as such
                                        flags.add(NoteFlag.ASCENDING);
                                        //Each second ascending note should get the STACKED_ASCENDING flag,
                                        //with some exceptions. The reason for the exceptions is that
                                        //a note with the STACKED_ASCENDING flag will be positioned directly
                                        //above the previous note, which is not always desired.
                                        if (!previousNote.flags.contains(NoteFlag.STACKED_ASCENDING)
                                                && !previousNote.flags.contains(NoteFlag.RHOMBUS)
                                                && !previousNote.flags.contains(NoteFlag.TORCULUS)
                                                && !previousNote.flags.contains(NoteFlag.THIRD_TORCULUS)
                                                && !flags.contains(NoteFlag.PORRECTUS)
                                                && !(flags.contains(NoteFlag.H_EPISEMA)
                                                    || previousNote.flags.contains(NoteFlag.H_EPISEMA))
                                                && !previousNote.flags.contains(NoteFlag.SCANDICUS)
                                                && !previousNote.flags.contains(NoteFlag.SECOND_CLIVIS)
                                                && !flags.contains(NoteFlag.CLIVIS)
                                                && !flags.contains(NoteFlag.QUILISMA)) {
                                            flags.add(NoteFlag.STACKED_ASCENDING);
                                            if (previousNote.flags.contains(NoteFlag.JUMP)) {
                                                previousNote.flags.remove(NoteFlag.JUMP);
                                            }
                                        }
                                    } else /*previousNote.value == noteValue*/ {
                                        previousNote.flags.add(NoteFlag.REPEATED);
                                    }
                                }
                                //create the new note (which is immediately the "previous" note)
                                previousNote = new ChantNote(noteValue, flags);
                                syllableNotes.add(previousNote);
                                //reset flags for the next note
                                flags = EnumSet.noneOf(NoteFlag.class);
                                addNote = false;
                            }
                        }
                        syllableNotesArray = new ChantNote[syllableNotes.size()];
                        syllableNotes.toArray(syllableNotesArray);
                        syllables.get(currentSection).add(new ChantSyllable(syllableText, syllableNotesArray, wordEnd, hasFlat, hasNeutral, 0));
                        hasFlat = false;

                        //reset the list of notes, so it can be used again
                        it = syllableNotes.iterator();
                        while (it.hasNext()) {
                            it.next();
                            it.remove();
                        }
                        break;
                }
            }
        }
        //do a second pass, adjusting the widths of notes and syllables,
        //having all flags properly in place, and knowing all the notes
        int width;
        int textWidth;
        float maxValue = 0;
        ChantNote note;
        LinkedList<ChantNote> hEpisemaNotes = new LinkedList<>();
        for(int s = 0; s < syllables.size(); s++) {
            for (ChantSyllable syllable : syllables.get(s)) {
                width = 0;
                if (syllable.hasFlat || syllable.hasNeutral) {
                    width = BASE_NOTE_OFFSET;
                }
                if (syllable.notes != null) {
                    for (int i = 0; i < syllable.notes.length; i++) {
                        note = syllable.notes[i];
                        if (i < syllable.notes.length - 1) {
                            width += note.adjustOffset(syllable.notes[i + 1]);
                        } else {
                            width += note.adjustOffset(null);//yes, this method will handle a NULL argument in a sane manner
                        }
                        //also, if sequential notes have horizontal episemas, get their heights to line up
                        if (note.flags.contains(NoteFlag.H_EPISEMA)) {
                            hEpisemaNotes.add(note);
                            maxValue = (maxValue > note.value) ? maxValue : note.value;
                        }
                        if (!note.flags.contains(NoteFlag.H_EPISEMA) || i == syllable.notes.length - 1) {
                            for (ChantNote hNote : hEpisemaNotes) {
                                hNote.episema_height = BASE_EPISEMA_HEIGHT + (int) ((maxValue - hNote.value) * STAFF_SPACE);
                            }
                            hEpisemaNotes.clear();
                            maxValue = 0;
                        }
                    }
                }
                textWidth = (int)textPaint.measureText(syllable.text);
                syllable.width = (textWidth > width) ? textWidth : width;
            }
        }
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int heightMeasure = measuredHeight > getMeasuredHeight() ? measuredHeight : getMeasuredHeight();
        setMeasuredDimension(getMeasuredWidth(),heightMeasure);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        measuredHeight = 400;
        float xOffset = 0;
        float yOffset = 0;
        currentSection = 0;

        //draw the error message(s), if there are any
        if(errorMessages.size() > 0) {
            Iterator<String> it = errorMessages.iterator();
            int count = 0;
            while(it.hasNext()) {
                canvas.drawText(it.next(), 40, 50 + 50*count, errorPaint);
                count++;
                yOffset += 50;
                measuredHeight += 50;
            }
        }

        //draw bar lines
        xOffset = STAFF_START_X;
        canvas.translate(xOffset,yOffset);
        int canvasWidth = canvas.getWidth();
        float[] barLinePoints = new float[16];
        for(int i = 0; i < 16; i += 4) {
            barLinePoints[i] = 0;
            barLinePoints[i + 1] = STAFF_START_Y + i/4 * STAFF_SPACE;
            barLinePoints[i + 2] = canvasWidth - STAFF_END_MARGIN - xOffset;
            barLinePoints[i + 3] = STAFF_START_Y + i/4 * STAFF_SPACE;
        }
        canvas.drawLines(barLinePoints,staffPaint);
        canvas.translate(xOffset,-yOffset);

        boolean needCustos = false;
        while (currentSection < syllables.size()) {
            drawClef(canvas, xOffset, yOffset);
            xOffset += CLEF_OFFSET;
            for (ChantSyllable syllable : syllables.get(currentSection)) {
                canvas.save();

                //check whether we need to start a new line
                if (xOffset + syllable.width > canvasWidth - WORD_OFFSET) {
                    needCustos = true;
                    xOffset = STAFF_START_X;
                    yOffset += 400;
                    measuredHeight += 400;

                    canvas.translate(xOffset, yOffset);
                    //draw a new set of bar lines
                    for (int i = 0; i < 16; i += 4) {
                        barLinePoints[i] = 0;
                        barLinePoints[i + 1] = STAFF_START_Y + i / 4 * STAFF_SPACE;
                        barLinePoints[i + 2] = canvasWidth - STAFF_END_MARGIN - xOffset;
                        barLinePoints[i + 3] = STAFF_START_Y + i / 4 * STAFF_SPACE;
                    }
                    canvas.drawLines(barLinePoints, staffPaint);
                    canvas.translate(-xOffset, -yOffset);
                    //draw a clef for the new line
                    drawClef(canvas, 0, yOffset);

                    xOffset += CLEF_OFFSET;
                }
                canvas.translate(xOffset, yOffset);
                if (needCustos) {
                    needCustos = false;
                    canvas.save();
                    canvas.translate(canvasWidth - BASE_NOTE_OFFSET * 2 - STAFF_END_MARGIN, -400);
                    if (syllable.notes != null) {
                        drawCustos(canvas, syllable.notes[0]);
                    }
                    canvas.restore();
                }
                drawSyllable(canvas, syllable);
                canvas.restore();

                if (syllable.wordEnd) {
                    xOffset += syllable.width + WORD_OFFSET;
                } else {
                    xOffset += syllable.width + SYLLABLE_OFFSET;
                }
            }
            currentSection++;
        }

        if (!drawnYet) {//Because this view waits until draw() is called to determine when to start
                        //a new line, it won't know until the first draw() how much height it needs.
                        //However, onMeasure() is called before the first draw() is called, so the
                        //view gives an initial (bad) estimate, and then fixes it here once it knows
                        //how much height it actually needs.
                        //This view finds out how much height it needs by updating the variable measuredHeight
                        //as it draws. onMeasure() then looks at measuredHeight when it is called.
            drawnYet = true;
            requestLayout();
        }
    }

    protected void drawClef(Canvas canvas, float xOffset, float yOffset) {
        canvas.save();
        canvas.translate(xOffset, yOffset);
        if(clef.get(currentSection) == Clef.FA) /*a fa clef is a do clef, plus a little bit*/ {
            //here we do the little bit: a punctum
            faClefFlags = EnumSet.noneOf(NoteFlag.class);
            previousNote = new ChantNote(clefLine.get(currentSection) - 1,faClefFlags);
            faClefFlags.add(NoteFlag.STACKED_ASCENDING);
            faClefFlags.add(NoteFlag.JUMP);
            drawNote(canvas, new ChantNote(clefLine.get(currentSection),faClefFlags));
            canvas.translate(BASE_NOTE_OFFSET+2,0);
        }
        canvas.translate(0, (5.5f - clefLine.get(currentSection)) * STAFF_SPACE);

        //upper part of the do clef
        clefPath.reset();
        clefPath.moveTo(10 + 5*SQRT_TWO, -20 - 5*SQRT_TWO);
        clefPath.lineTo(10 + 5*SQRT_TWO, - 5*SQRT_TWO);
        arcRectF.set(0,-10,20,10);

        clefPath.arcTo(arcRectF,315,-135);
        clefPath.lineTo(0, -20);
        arcRectF.set(0,-30,20,-10);
        clefPath.arcTo(arcRectF,180,135);
        clefPath.close();
        canvas.drawPath(clefPath, notePaint);

        //lower part of the do clef
        clefPath.reset();
        clefPath.moveTo(10 + 5*SQRT_TWO, 20 + 5*SQRT_TWO);
        clefPath.lineTo(10 + 5*SQRT_TWO, 5*SQRT_TWO);
        arcRectF.set(0,-10,20,10);

        clefPath.arcTo(arcRectF,45,135);
        clefPath.lineTo(0, 0);
        arcRectF.set(0,10,20,30);
        clefPath.arcTo(arcRectF,180,-135);
        clefPath.close();
        canvas.drawPath(clefPath, notePaint);

        canvas.restore();
    }

    protected void drawSyllable(Canvas canvas, ChantSyllable syllable) {
        //lyrics
        canvas.drawText(syllable.text, 0, STAFF_START_Y + 4 * STAFF_SPACE + TEXT_START_OFFSET, textPaint);
        //flat, if there is one
        if (syllable.hasFlat) {
            drawFlat(canvas);
            canvas.translate(BASE_NOTE_OFFSET,0);
        } else if (syllable.hasNeutral) {
            drawNeutral(canvas);
            canvas.translate(BASE_NOTE_OFFSET,0);
        }
        //notes
        if (syllable.notes != null) {
            for(ChantNote note : syllable.notes) {
                drawNote(canvas, note);
                canvas.translate(note.offset,0);
            }
        }
    }

    protected void drawNote(Canvas canvas, ChantNote note) {
        notePath.reset();
        canvas.save();

        if(note.flags.contains(NoteFlag.QUARTER_BAR)) {
            //draw a quarter bar
            canvas.drawLine(0, STAFF_START_Y - STAFF_SPACE/2, 0, STAFF_START_Y + STAFF_SPACE / 2, staffPaint);
        } else if(note.flags.contains(NoteFlag.HALF_BAR)) {
            //draw a half bar
            canvas.drawLine(0, STAFF_START_Y + STAFF_SPACE/2, 0, STAFF_START_Y + STAFF_SPACE * 2.5f, staffPaint);
        } else if(note.flags.contains(NoteFlag.FULL_BAR)) {
            //draw a full bar
            canvas.drawLine(0, STAFF_START_Y, 0, STAFF_START_Y + STAFF_SPACE * 3, staffPaint);
        } else if(note.flags.contains(NoteFlag.DOUBLE_BAR)) {
            //draw a double bar
            canvas.drawLine(-5, STAFF_START_Y, -5, STAFF_START_Y + STAFF_SPACE * 3, staffPaint);
            canvas.drawLine(5, STAFF_START_Y, 5, STAFF_START_Y + STAFF_SPACE * 3, staffPaint);
        } else {
            //a normal note, not a bar
            canvas.translate(0, STAFF_START_Y + STAFF_SPACE * (4 - note.value) - 10);
            if (note.flags.contains(NoteFlag.JUMP) && 
                    !note.flags.contains(NoteFlag.RHOMBUS) &&
                    !previousNote.flags.contains(NoteFlag.DOT) &&
                    !note.flags.contains(NoteFlag.SCANDICUS) &&
                    !note.flags.contains(NoteFlag.QUILISMA)) {
                if (note.flags.contains(NoteFlag.STACKED_ASCENDING)) {
                    //draw a line from the level of the previous note to this note, on the right side of this note
                    canvas.drawLine(19, 10, 19, STAFF_SPACE * (note.value - previousNote.value), staffPaint);
                } else if (!note.flags.contains(NoteFlag.SECOND_PORRECTUS)){
                    //draw a line from the level of the previous note to this note, on the left side of this note
                    canvas.drawLine(1, 10, 1, STAFF_SPACE * (note.value - previousNote.value), staffPaint);
                }
            }
            if (note.flags.contains(NoteFlag.FIRST_DESCENDING) &&
                    !note.flags.contains(NoteFlag.PEAK) &&
                    !note.flags.contains(NoteFlag.RHOMBUS)&&
                    !note.flags.contains(NoteFlag.THIRD_TORCULUS)) {
                //draw a descending stem for this note
                canvas.drawLine(1, 10, 1, STAFF_SPACE, staffPaint);
            }
            if (note.flags.contains(NoteFlag.LIQUESCENT)) {
                //draw a line from the previous note to this note
                canvas.drawLine(19, 10, 19, STAFF_SPACE * (note.value - previousNote.value), staffPaint);
                //draw the note smaller
                canvas.translate(5,5);
                canvas.scale(0.75f,0.75f);
            }
            if (note.flags.contains(NoteFlag.DOT)) {
                canvas.drawCircle(27.5f, -5, 5, notePaint);
            }
            if (note.flags.contains(NoteFlag.H_EPISEMA)) {
                //draw a horizontal line above this note
                canvas.drawLine(0,-note.episema_height,note.offset,-note.episema_height,episemaPaint);
            }
            if (note.flags.contains(NoteFlag.V_EPISEMA)) {
                if (note.flags.contains(NoteFlag.STACKED_ASCENDING)) {
                    //draw a vertical line above this note
                    canvas.drawLine(10,-15,10,-30,episemaPaint);
                } else {
                    canvas.drawLine(10,35,10,50,episemaPaint);
                }
            }
            if (note.flags.contains(NoteFlag.PORRECTUS)) {
                //do nothing; wait until the second part of the porrectus to draw
            } else if (note.flags.contains(NoteFlag.SECOND_PORRECTUS)) {
                notePath.moveTo(-50,(note.value - previousNote.value) * STAFF_SPACE);
                notePath.lineTo(20,0);
                notePath.lineTo(20,20);
                notePath.lineTo(-50,(note.value - previousNote.value) * STAFF_SPACE + 20);
                notePath.lineTo(-50,(note.value - previousNote.value) * STAFF_SPACE);
                notePath.close();
                canvas.drawPath(notePath, notePaint);
            } else if (note.flags.contains(NoteFlag.QUILISMA)) {
                //draw the quilisma (that is, just the jagged punctum)
                notePath.moveTo(0,30);
                notePath.lineTo(0,-5);
                notePath.lineTo(5,10);
                notePath.lineTo(5,-5);
                notePath.lineTo(10,10);
                notePath.lineTo(10,-5);
                notePath.lineTo(15,10);
                notePath.lineTo(15,-5);
                notePath.lineTo(18,4);
                notePath.lineTo(20,-10);
                notePath.lineTo(20,25);
                notePath.lineTo(15,10);
                notePath.lineTo(15,25);
                notePath.lineTo(10,10);
                notePath.lineTo(10,25);
                notePath.lineTo(5,10);
                notePath.lineTo(5,25);
                notePath.lineTo(2,16);
                notePath.lineTo(0,30);
                notePath.close();
                canvas.drawPath(notePath, notePaint);
            } else if (note.flags.contains(NoteFlag.RHOMBUS)) {
                //draw a rhombus
                notePath.moveTo(10,-5);
                notePath.lineTo(20,10);
                notePath.lineTo(10,25);
                notePath.lineTo(0,10);
                notePath.close();
                canvas.drawPath(notePath,notePaint);
            } else {
                //draw a punctum
                notePath.moveTo(0, 0);
                arcRectF.set(10 - 10 * SQRT_TWO, 10 - 10 * SQRT_TWO,
                        10 + 10 * SQRT_TWO, 10 + 10 * SQRT_TWO);
                notePath.arcTo(arcRectF, 225, 90);
                notePath.lineTo(20, 20);
                arcRectF.set(10 - 10 * SQRT_TWO, 30 - 10 * SQRT_TWO,
                        10 + 10 * SQRT_TWO, 30 + 10 * SQRT_TWO);
                notePath.arcTo(arcRectF, 315, -90);
                notePath.lineTo(0, 0);
                canvas.drawPath(notePath, notePaint);
            }
        }
        previousNote = note;
        canvas.restore();
    }

    protected void drawCustos(Canvas canvas, ChantNote note) {
        canvas.translate(0, STAFF_START_Y + STAFF_SPACE * (4 - note.value) - 10);

        if (note.flags.contains(NoteFlag.H_EPISEMA)) {
            canvas.drawLine(-BASE_NOTE_OFFSET/2,-note.episema_height,BASE_NOTE_OFFSET/2,-note.episema_height,episemaPaint);
        }

        //a custos is drawn as half a punctum
        //I have decided to temporarily just draw a rectangle
        //instead of the fancier half-punctum I was attempting
        canvas.drawRect(-BASE_NOTE_OFFSET/2,0,0,BASE_NOTE_OFFSET,notePaint);
    }

    protected void drawFlat(Canvas canvas) {
        canvas.save();
        if (clef.get(currentSection) == Clef.DO) {
            canvas.translate(0, STAFF_START_Y + STAFF_SPACE * (4 - clefLine.get(currentSection)) +10);
        } else /*Fa Clef*/ {
            if (clefLine.get(currentSection) < 3.5) {
                canvas.translate(0, STAFF_START_Y + STAFF_SPACE * (2 - clefLine.get(currentSection)) + 10);
            } else {
                canvas.translate(0, STAFF_START_Y + STAFF_SPACE * (5.5f - clefLine.get(currentSection)) + 10);
            }
        }
        canvas.drawLine(0,-10,0,20,staffPaint);
        canvas.drawLine(0,20,10,25,staffPaint);
        canvas.drawLine(10,25,10,15,staffPaint);
        canvas.drawLine(10,15,0,10,staffPaint);
        canvas.restore();
    }

    protected void drawNeutral(Canvas canvas) {
        canvas.save();
        if (clef.get(currentSection) == Clef.DO) {
            canvas.translate(0, STAFF_START_Y + STAFF_SPACE * (4 - clefLine.get(currentSection)) +10);
        } else /*Fa Clef*/ {
            if (clefLine.get(currentSection) < 3.5) {
                canvas.translate(0, STAFF_START_Y + STAFF_SPACE * (2 - clefLine.get(currentSection)) + 10);
            } else {
                canvas.translate(0, STAFF_START_Y + STAFF_SPACE * (5.5f - clefLine.get(currentSection)) + 10);
            }
        }
        canvas.drawLine(0,-10,0,25,staffPaint);
        canvas.drawLine(0,25,10,20,staffPaint);
        canvas.drawLine(0,15,10,10,staffPaint);
        canvas.drawLine(10,10,10,45,staffPaint);
        canvas.restore();
    }
}
