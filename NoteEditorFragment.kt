package com.notevault.ui.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.notevault.R
import com.notevault.databinding.FragmentNoteEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NoteEditorFragment : Fragment() {

    private var _binding: FragmentNoteEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NoteEditorViewModel by viewModels()
    private val args: NoteEditorFragmentArgs by navArgs()

    private lateinit var attachmentAdapter: AttachmentAdapter

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> uris.forEach { ensureNoteExists { viewModel.addAttachment(it) } } }

    private var cameraUri: Uri? = null
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { ensureNoteExists { viewModel.addAttachment(it) } } }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteEditorBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAttachments()
        setupBottomBar()
        observeViewModel()
        handleSharedContent()
    }

    private fun setupAttachments() {
        attachmentAdapter = AttachmentAdapter(
            onAttachmentClick = { attachment ->
                val currentNote = viewModel.note.value ?: return@AttachmentAdapter
                val index = attachmentAdapter.currentList.indexOf(attachment)
                findNavController().navigate(
                    NoteEditorFragmentDirections.actionEditorToViewer(
                        noteId = currentNote.id,
                        initialIndex = index.coerceAtLeast(0)
                    )
                )
            },
            onAttachmentDelete = { att ->
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Удалить вложение?")
                    .setMessage(att.fileName)
                    .setPositiveButton("Удалить") { _, _ -> viewModel.deleteAttachment(att) }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        )
        binding.rvAttachments.apply {
            adapter = attachmentAdapter
            layoutManager = GridLayoutManager(requireContext(), 3)
        }
    }

    private fun setupBottomBar() {
        binding.btnCamera.setOnClickListener { launchCamera() }
        binding.btnAttachImage.setOnClickListener { pickFileLauncher.launch("image/*") }
        binding.btnAttachFile.setOnClickListener { pickFileLauncher.launch("*/*") }
        binding.btnAddTag.setOnClickListener { showTagDialog() }
    }

    private fun launchCamera() {
        val photoFile = java.io.File.createTempFile(
            "photo_${System.currentTimeMillis()}", ".jpg",
            requireContext().cacheDir
        )
        cameraUri = androidx.core.content.FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile
        )
        cameraLauncher.launch(cameraUri)
    }

    private fun showTagDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "Новый тег"
        }
        val container = android.widget.FrameLayout(requireContext()).apply {
            val pad = resources.getDimensionPixelSize(R.dimen.content_padding)
            setPadding(pad, 0, pad, 0)
            addView(input)
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Добавить тег")
            .setView(container)
            .setPositiveButton("Добавить") { _, _ ->
                val tag = input.text?.toString()?.trim() ?: return@setPositiveButton
                if (tag.isNotBlank()) viewModel.addTag(tag)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.note.collect { note ->
                        note?.let {
                            if (binding.etTitle.text.isNullOrEmpty())
                                binding.etTitle.setText(it.title)
                            if (binding.etContent.text.isNullOrEmpty())
                                binding.etContent.setText(it.content)
                        }
                    }
                }
                launch {
                    viewModel.attachments.collect { list ->
                        attachmentAdapter.submitList(list)
                        binding.rvAttachments.isVisible = list.isNotEmpty()
                    }
                }
                launch {
                    viewModel.tags.collect { tags ->
                        binding.chipGroupTags.removeAllViews()
                        tags.forEach { tag ->
                            val chip = Chip(requireContext()).apply {
                                text = "#$tag"
                                isCloseIconVisible = true
                                setOnCloseIconClickListener { viewModel.removeTag(tag) }
                            }
                            binding.chipGroupTags.addView(chip)
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is EditorEvent.NoteSaved ->
                                Snackbar.make(binding.root, "✓ Сохранено", Snackbar.LENGTH_SHORT).show()
                            is EditorEvent.NoteDeleted ->
                                findNavController().popBackStack()
                            is EditorEvent.ShowError ->
                                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun handleSharedContent() {
        val intent = requireActivity().intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                when {
                    intent.type == "text/plain" -> {
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                        binding.etContent.setText(text)
                        binding.etTitle.setText(text.lines().firstOrNull()?.take(60) ?: "")
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                            ensureNoteExists { viewModel.addAttachment(uri) }
                        }
                    }
                }
                requireActivity().intent = Intent()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach { uri ->
                    ensureNoteExists { viewModel.addAttachment(uri) }
                }
                requireActivity().intent = Intent()
            }
        }
    }

    private fun ensureNoteExists(then: () -> Unit) {
        val existing = viewModel.note.value
        if (existing != null) { then(); return }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saveNote()
            viewModel.note.filterNotNull().first().let { then() }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_editor, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_save -> {
            syncFieldsToViewModel()
            viewModel.saveNote()
            true
        }
        R.id.action_delete -> { confirmDelete(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun syncFieldsToViewModel() {
        viewModel.title.value = binding.etTitle.text.toString()
        viewModel.content.value = binding.etContent.text.toString()
    }

    private fun confirmDelete() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Удалить заметку?")
            .setMessage("Все вложения тоже будут удалены.")
            .setPositiveButton("Удалить") { _, _ -> viewModel.deleteNote() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        syncFieldsToViewModel()
        if (viewModel.title.value.isNotBlank() || viewModel.content.value.isNotBlank()) {
            viewModel.saveNote()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
